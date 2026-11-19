package dev.no.name.afriends.net.game;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import dev.no.name.afriends.AFriends;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import dev.no.name.afriends.compat.Compat;
import dev.no.name.afriends.core.GroupPoll;
import dev.no.name.afriends.core.MessageContent;
import dev.no.name.afriends.core.MessageImage;
import dev.no.name.afriends.core.MessageReply;
import dev.no.name.afriends.core.PrivateMessage;
import dev.no.name.afriends.core.PrivateMessages;
import dev.no.name.afriends.core.ServerScope;
import dev.no.name.afriends.core.SocialData;
import dev.no.name.afriends.net.chat.InboxListener;
import dev.no.name.afriends.net.model.LinkFriend;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.List;

// Расшифрованные сообщения из сети — в переписку AFriends на потоке игры.
// В меню переписки нет, поэтому сообщения ждут входа в мир
final class IncomingChat implements InboxListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(AFriends.NAME);
    private static final String UNREADABLE = "⚠ Сообщение не расшифровалось. Если повторится — сверьте отпечаток ключей";

    private final SocialData social;
    // сообщение расшифровалось: автору уходит «доставлено» (peerId, id сообщения); зовётся на потоке игры
    private BiConsumer<String, String> onDelivered = (peer, id) -> { };
    // группы, из которых игрок только что вышел: их сообщения больше не показываем
    private Predicate<String> leftGroup = group -> false;
    // отложенные до входа в мир — свои у каждого аккаунта, лежат в его папке ключей
    private ParkedMessages parked;

    IncomingChat(SocialData social, Path parkedFile) {
        this.social = social;
        this.parked = new ParkedMessages(parkedFile);
    }

    void setOnDelivered(BiConsumer<String, String> listener) {
        onDelivered = listener;
    }

    void setLeftGroup(Predicate<String> rule) {
        leftGroup = rule;
    }

    @Override
    public void text(LinkFriend from, String text, long time) {
        Minecraft.getInstance().execute(() -> deliver(from.name(), new PrivateMessage(false, text, time)));
    }

    @Override
    public void message(LinkFriend from, String text, String id, MessageImage image, String forwardedFrom,
                        MessageReply reply, long time) {
        List<MessageImage> images = image == null ? List.of() : List.of(image);
        Minecraft.getInstance().execute(() -> {
            if (!social.ignored().isIgnored(from.name())) {
                onDelivered.accept(from.id(), id);
            }
            deliver(from.name(), new PrivateMessage(false, text, time, id, images, null, null, forwardedFrom, reply));
        });
    }

    // собеседник получил или прочитал наши сообщения
    @Override
    public void receipt(LinkFriend from, List<String> targets, boolean read, long time) {
        Minecraft.getInstance().execute(() -> social.accountWide().receipt(targets,
                read ? PrivateMessages.RECEIPT_READ : PrivateMessages.RECEIPT_DELIVERED));
    }

    // описание / аватарка группы; права участника проверены до вызова
    @Override
    public void groupMeta(String group, LinkFriend from, String name, String description, MessageImage avatar, long time) {
        Minecraft.getInstance().execute(() -> {
            if (leftGroup.test(group)) {
                return;
            }
            String chat = PrivateMessages.groupKey(group);
            if (name != null) {
                social.messages().setTitle(chat, name);
            }
            if (description != null) {
                social.messages().setDescription(chat, description);
            }
            if (avatar != null) {
                social.messages().setAvatar(chat, avatar);
            }
            social.messages().ensureChat(chat);
        });
    }

    @Override
    public void groupNameOffered(String group, LinkFriend from, String name, long time) {
        Minecraft.getInstance().execute(() -> {
            if (leftGroup.test(group)) {
                return;
            }
            String chat = PrivateMessages.groupKey(group);
            if (!social.messages().hasTitle(chat)) {
                social.messages().setTitle(chat, name);
                social.messages().ensureChat(chat);
            }
        });
    }

    @Override
    public void groupRejected(String group, LinkFriend from, long time) {
        LOGGER.info("{}: сообщение {} в группу {} отброшено — у него нет на это права", AFriends.NAME, from.name(), group);
    }

    // друг удалил у обоих: ищем во всех серверах, как реакцию. Игнор не мешает — это его же переписка с нами
    @Override
    public void deleted(LinkFriend from, List<String> targets, long time) {
        Minecraft.getInstance().execute(() -> social.accountWide().remove(from.name(), targets));
    }

    @Override
    public void pinned(LinkFriend from, String target, boolean pinned, long time) {
        Minecraft.getInstance().execute(() -> {
            if (!social.ignored().isIgnored(from.name())) {
                social.accountWide().pin(from.name(), target, pinned);
            }
        });
    }

    // реакция друга: сообщение ищется по id во всех серверах — она могла прийти и в главном меню
    @Override
    public void reaction(LinkFriend from, String target, String reaction, long time) {
        Minecraft.getInstance().execute(() -> {
            if (!social.ignored().isIgnored(from.name())) {
                social.accountWide().react(from.name(), target, false, reaction);
            }
        });
    }

    // групповой чат: автор подписан над сообщением; игнор работает и здесь
    @Override
    public void groupMessage(String group, LinkFriend from, String text, String id, MessageImage image, MessageReply reply, long time) {
        List<MessageImage> images = image == null ? List.of() : List.of(image);
        // в группе «доставлено» не шлём: на каждое сообщение каждому автору — слишком много конвертов
        Minecraft.getInstance().execute(() -> {
            if (!leftGroup.test(group) && !social.ignored().isIgnored(from.name())) {
                deliver(PrivateMessages.groupKey(group),
                        new PrivateMessage(false, text, time, id, images, null, null, null, reply, from.name()));
            }
        });
    }

    @Override
    public void groupPoll(String group, LinkFriend from, String text, String id, List<String> options, MessageReply reply, long time) {
        Minecraft.getInstance().execute(() -> {
            if (!leftGroup.test(group) && !social.ignored().isIgnored(from.name())) {
                deliver(PrivateMessages.groupKey(group), new PrivateMessage(false, text, time, id, List.of(), null, null, null, reply,
                        from.name(), GroupPoll.fresh(options)));
            }
        });
    }

    // голос участника: опрос ищем во всех серверах, как реакцию. Голос того, кого игнорируешь, всё равно считается
    @Override
    public void groupVote(String group, LinkFriend from, String target, int choice, long time) {
        Minecraft.getInstance().execute(() -> social.accountWide().vote(PrivateMessages.groupKey(group), target, from.name(), choice));
    }

    @Override
    public void groupNamed(String group, LinkFriend from, String name, long time) {
        Minecraft.getInstance().execute(() -> {
            if (leftGroup.test(group)) {
                return;
            }
            String chat = PrivateMessages.groupKey(group);
            social.messages().setTitle(chat, name);
            social.messages().ensureChat(chat);
        });
    }

    @Override
    public void unreadable(LinkFriend from, long time) {
        Minecraft.getInstance().execute(() -> deliver(from.name(), new PrivateMessage(false, UNREADABLE, time)));
    }

    @Override
    public void official(String text, List<MessageImage> images, long time) {
        Minecraft.getInstance().execute(() -> deliver(PrivateMessages.OFFICIAL, new PrivateMessage(false, text, time, null, images, null, null)));
    }

    // игрок сменил аккаунт: отложенное прежнего остаётся в его папке и дождётся его возвращения
    void useParked(Path file) {
        parked = new ParkedMessages(file);
    }

    // раз в тик: зашли в мир — отложенное раскладывается по перепискам
    void tick() {
        if (!parked.isEmpty() && !ServerScope.NONE.equals(ServerScope.current())) {
            parked.takeAll().forEach(item -> deliver(item.partner(), item.message()));
        }
    }

    private void deliver(String partner, PrivateMessage message) {
        if (ServerScope.NONE.equals(ServerScope.current())) {
            parked.add(partner, message);
            return;
        }
        // AFriends не игнорируется: это сообщения от админов мода
        if (PrivateMessages.isOfficial(partner)) {
            social.messages().add(partner, message);
            Compat.localMessage(officialLine(chatText(message)));
            return;
        }
        if (social.ignored().isIgnored(partner)) {
            return;
        }
        social.messages().add(partner, message);
    }

    // в игровой чат — без меток [img]; если есть картинки, пометка о них
    private static String chatText(PrivateMessage message) {
        String plain = MessageContent.plain(message.text());
        if (message.images().isEmpty()) {
            return plain;
        }
        String mark = message.images().size() == 1
                ? (message.image().voice() ? "[Голосовое]" : message.image().animated() ? "[Гифка]" : "[Изображение]")
                : "[Изображения: " + message.images().size() + "]";
        return plain.isEmpty() ? mark : plain + " " + mark;
    }

    // строка в игровом чате: «AFriends » красным, текст белым
    private static Component officialLine(String text) {
        return Component.literal("[" + PrivateMessages.OFFICIAL_NAME + "] ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.WHITE));
    }
}
