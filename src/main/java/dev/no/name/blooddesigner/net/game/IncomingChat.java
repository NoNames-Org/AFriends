package dev.no.name.blooddesigner.net.game;

import dev.no.name.blooddesigner.compat.Compat;
import dev.no.name.blooddesigner.core.MessageImage;
import dev.no.name.blooddesigner.core.PrivateMessage;
import dev.no.name.blooddesigner.core.PrivateMessages;
import dev.no.name.blooddesigner.core.ServerScope;
import dev.no.name.blooddesigner.core.SocialData;
import dev.no.name.blooddesigner.net.chat.InboxListener;
import dev.no.name.blooddesigner.net.model.LinkFriend;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

// Расшифрованные сообщения из сети — в переписку BloodDesigner на потоке игры.
// В меню переписки нет, поэтому сообщения ждут входа в мир
final class IncomingChat implements InboxListener {
    private static final String UNREADABLE = "⚠ Сообщение не расшифровалось. Если повторится — сверьте отпечаток ключей";

    private final SocialData social;
    private final ParkedMessages parked;

    IncomingChat(SocialData social, Path parkedFile) {
        this.social = social;
        this.parked = new ParkedMessages(parkedFile);
    }

    @Override
    public void text(LinkFriend from, String text, long time) {
        Minecraft.getInstance().execute(() -> deliver(from.name(), new PrivateMessage(false, text, time)));
    }

    @Override
    public void message(LinkFriend from, String text, String id, MessageImage image, long time) {
        Minecraft.getInstance().execute(() -> deliver(from.name(), new PrivateMessage(false, text, time, id, image)));
    }

    // реакция друга: сообщение ищется по id во всех серверах — она могла прийти и в главном меню
    @Override
    public void reaction(LinkFriend from, String target, String reaction, long time) {
        Minecraft.getInstance().execute(() -> {
            if (!social.ignored().isIgnored(from.name())) {
                social.react(from.name(), target, false, reaction);
            }
        });
    }

    @Override
    public void unreadable(LinkFriend from, long time) {
        Minecraft.getInstance().execute(() -> deliver(from.name(), new PrivateMessage(false, UNREADABLE, time)));
    }

    @Override
    public void official(String text, long time) {
        Minecraft.getInstance().execute(() -> deliver(PrivateMessages.OFFICIAL, new PrivateMessage(false, text, time)));
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
        // BloodDesigner не игнорируется: это сообщения от админов мода
        if (PrivateMessages.isOfficial(partner)) {
            social.messages().add(partner, message);
            Compat.localMessage(officialLine(message.text()));
            return;
        }
        if (social.ignored().isIgnored(partner)) {
            return;
        }
        social.messages().add(partner, message);
    }

    // строка в игровом чате: «BloodDesigner » красным, текст белым
    private static Component officialLine(String text) {
        return Component.literal("[" + PrivateMessages.OFFICIAL_NAME + "] ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.WHITE));
    }
}
