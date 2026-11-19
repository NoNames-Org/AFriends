package dev.no.name.afriends.net.game;

import dev.no.name.afriends.net.service.OutgoingText;
import dev.no.name.afriends.addon.SendPolicy;
import dev.no.name.afriends.net.model.LinkGroup;
import dev.no.name.afriends.core.voice.VoiceClip;
import dev.no.name.afriends.core.ProfileRoute;
import dev.no.name.afriends.core.FriendManager;
import dev.no.name.afriends.core.GroupChats;
import dev.no.name.afriends.core.MessageRoute;
import dev.no.name.afriends.core.MessageImage;
import dev.no.name.afriends.core.MessageReply;
import dev.no.name.afriends.core.PrivateMessage;
import dev.no.name.afriends.core.PrivateMessages;
import dev.no.name.afriends.core.SocialData;
import dev.no.name.afriends.core.MessageIds;
import dev.no.name.afriends.net.chat.ChatPayload;
import dev.no.name.afriends.net.media.PreparedImage;
import dev.no.name.afriends.net.model.Connection;
import dev.no.name.afriends.net.rpc.Failures;
import dev.no.name.afriends.net.model.LinkFriend;
import dev.no.name.afriends.net.model.LinkSnapshot;
import dev.no.name.afriends.net.service.LinkService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

// Куда уходит ЛС: другу по сети — сквозным шифрованием через свой сервер, игроку без мода — по-старому /msg.
// Всё на потоке игры
public final class DirectMessages implements MessageRoute {
    // образцы вложений для проверки прав в группе до чтения файла
    private static final MessageImage VOICE_KIND = new MessageImage("", null, VoiceClip.MIME, 0, 0, 0, "");
    private static final MessageImage GIF_KIND = new MessageImage("", null, "image/gif", 0, 0, 0, "");
    private static final MessageImage IMAGE_KIND = new MessageImage("", null, "image/png", 0, 0, 0, "");
    private final SocialData social;
    private final LinkService link;
    private final OfficialRoute official;
    private final NotesRoute notes;
    private final GroupRoute groups;
    private final NetworkProfile profile;
    private final ReceiptSender receipts;
    // аддоны могут запретить /msg (например, на сервере с антиспамом)
    private final SendPolicy sendPolicy;

    DirectMessages(SocialData social, LinkService link, SendPolicy sendPolicy) {
        this.social = social;
        this.link = link;
        this.sendPolicy = sendPolicy;
        this.official = new OfficialRoute(social, link);
        this.notes = new NotesRoute(social, link.media());
        this.groups = new GroupRoute(social, link);
        this.profile = new NetworkProfile(link);
        this.receipts = new ReceiptSender(link);
    }

    @Override
    public GroupChats groups() {
        return groups;
    }

    @Override
    public ProfileRoute profile() {
        return profile;
    }

    void tick() {
        groups.tick();
        receipts.tick();
    }

    // вышли из группы, а sync ещё не убрал её: опоздавшие сообщения не возвращают чат
    boolean hasLeftGroup(String group) {
        return groups.hasLeft(group);
    }

    // сообщение по сети расшифровалось: автору — «доставлено»
    void delivered(String peerId, String messageId) {
        receipts.delivered(peerId, messageId);
    }

    @Override
    public void markRead(String chat, List<PrivateMessage> seen) {
        LinkSnapshot state = link.snapshot();
        LinkGroup group = PrivateMessages.isGroup(chat) ? state.group(PrivateMessages.groupId(chat)) : null;
        LinkFriend direct = group == null ? state.directNamed(chat) : null;
        for (PrivateMessage message : seen) {
            if (message.fromMe() || message.id() == null) {
                continue;
            }
            LinkFriend author = group != null ? (message.author() == null ? null : group.memberNamed(message.author())) : direct;
            if (author != null) {
                receipts.read(author.id(), message.id());
            }
        }
    }

    // друг по сети: пишется через сервер сети, и неважно, на каком сервере Minecraft он сейчас
    @Override
    public boolean viaNetwork(String partner) {
        return partner != null && (PrivateMessages.isGroup(partner) || link.snapshot().directNamed(partner) != null);
    }

    // Отправить. Пусто — ушло в обработку; иначе причина, почему не ушло сразу.
    // Если сеть откажет позже, причина придёт в failed, а сообщение будет помечено неотправленным
    @Override
    public Optional<String> send(String partner, String text, Consumer<String> failed) {
        return send(partner, text, null, failed);
    }

    @Override
    public Optional<String> send(String partner, String text, String forwardedFrom, Consumer<String> failed) {
        return send(partner, text, forwardedFrom, null, failed);
    }

    @Override
    public Optional<String> send(String partner, String text, String forwardedFrom, MessageReply reply, Consumer<String> failed) {
        LinkSnapshot state = link.snapshot();
        if (PrivateMessages.NOTES.equals(partner)) {
            return notes.send(text, forwardedFrom, reply);
        }
        // AFriends: админам мода через сервер сети, на портал
        if (PrivateMessages.isOfficial(partner)) {
            return official.send(text, forwardedFrom, failed);
        }
        if (PrivateMessages.isGroup(partner)) {
            String shown = forwardedFrom == null ? text : MessageRoute.forwardedPrefix(forwardedFrom) + text;
            return groups.send(partner, shown, reply, failed);
        }
        LinkFriend friend = state.directNamed(partner);
        if (friend != null) {
            if (state.connection() != Connection.ONLINE) {
                return Optional.of(LinkText.sendProblem("offline"));
            }
            String id = MessageIds.next();
            link.direct().sendText(friend.id(), new OutgoingText(text, id, forwardedFrom, reply)).thenAccept(code -> Minecraft.getInstance().execute(() -> {
                long now = System.currentTimeMillis();
                if ("ok".equals(code)) {
                    social.messages().add(partner, new PrivateMessage(true, text, now, id, List.of(), null, null, forwardedFrom, reply));
                } else {
                    social.messages().markOffline(partner, now);
                    failed.accept(LinkText.sendProblem(code));
                }
            }));
            return Optional.empty();
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return Optional.of("Нет соединения с сервером");
        }
        // через /msg сервера пометку не передать — она строкой в начале; переводы строк /msg не держит
        String shown = forwardedFrom == null ? text : MessageRoute.forwardedPrefix(forwardedFrom).replace("\n", " ") + text;
        String command = "msg " + partner + " " + shown.replace('\n', ' ');
        Optional<String> problem = sendPolicy.sendProblem(partner, command);
        if (problem.isEmpty()) {
            connection.sendCommand(command);
        }
        return problem;
    }

    // картинки: другу по сети, админам в AFriends и себе в «Избранное»; через /msg — нет
    @Override
    public boolean supportsMedia(String partner) {
        return partner != null && (PrivateMessages.NOTES.equals(partner) || PrivateMessages.isOfficial(partner)
                || PrivateMessages.isGroup(partner) || link.snapshot().directNamed(partner) != null);
    }

    @Override
    public void keepNote(String text, List<MessageImage> images, String forwardedFrom) {
        notes.keep(text, images, forwardedFrom);
    }

    // файл читается и проверяется не на потоке игры: GIF на 5 МБ разбирать заметно
    @Override
    public Optional<String> sendImage(String partner, Path file, String caption, Consumer<String> failed) {
        return sendImage(partner, file, caption, null, failed);
    }

    @Override
    public Optional<String> sendImage(String partner, Path file, String caption, String forwardedFrom, Consumer<String> failed) {
        return sendImage(partner, file, caption, forwardedFrom, null, failed);
    }

    @Override
    public Optional<String> sendImage(String partner, Path file, String caption, String forwardedFrom, MessageReply reply,
                                      Consumer<String> failed) {
        if (PrivateMessages.NOTES.equals(partner)) {
            return notes.sendImage(file, caption, forwardedFrom, reply, failed);
        }
        if (PrivateMessages.isOfficial(partner)) {
            return official.sendImage(file, caption, forwardedFrom, failed);
        }
        if (PrivateMessages.isGroup(partner)) {
            return groups.sendMedia(partner, () -> PreparedImage.read(file), imageKind(file), caption, reply, failed);
        }
        LinkSnapshot state = link.snapshot();
        LinkFriend friend = state.directNamed(partner);
        if (friend == null) {
            return Optional.of("Картинки можно отправлять только друзьям по сети");
        }
        if (state.connection() != Connection.ONLINE) {
            return Optional.of(LinkText.sendProblem("offline"));
        }
        String id = MessageIds.next();
        CompletableFuture.supplyAsync(() -> PreparedImage.read(file))
                .thenCompose(prepared -> link.direct().sendImage(friend.id(), prepared, new OutgoingText(caption, id, forwardedFrom, reply)))
                .whenComplete((image, error) -> Minecraft.getInstance().execute(() -> {
                    if (error == null) {
                        social.messages().add(partner, new PrivateMessage(true, caption, System.currentTimeMillis(), id,
                                List.of(image), null, null, forwardedFrom, reply));
                    } else {
                        failed.accept(imageProblem(Failures.codeOf(error)));
                    }
                }));
        return Optional.empty();
    }

    @Override
    public Optional<String> sendVoice(String partner, byte[] data, int centiseconds, MessageReply reply, Consumer<String> failed) {
        if (PrivateMessages.isGroup(partner)) {
            PreparedImage voice = PreparedImage.voice(data, centiseconds);
            return groups.sendMedia(partner, () -> voice, VOICE_KIND, "", reply, failed);
        }
        LinkSnapshot state = link.snapshot();
        LinkFriend friend = state.directNamed(partner);
        if (friend == null) {
            return Optional.of("Голосовые можно отправлять только друзьям по сети");
        }
        if (state.connection() != Connection.ONLINE) {
            return Optional.of(LinkText.sendProblem("offline"));
        }
        String id = MessageIds.next();
        link.direct().sendImage(friend.id(), PreparedImage.voice(data, centiseconds), new OutgoingText("", id, null, reply))
                .whenComplete((voice, error) -> Minecraft.getInstance().execute(() -> {
                    if (error == null) {
                        social.messages().add(partner, new PrivateMessage(true, "", System.currentTimeMillis(), id,
                                List.of(voice), null, null, null, reply));
                    } else {
                        failed.accept(imageProblem(Failures.codeOf(error)));
                    }
                }));
        return Optional.empty();
    }

    private static MessageImage imageKind(Path file) {
        String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".gif") ? GIF_KIND : IMAGE_KIND;
    }

    // своя реакция сразу видна у себя; не ушла — откатываем
    @Override
    public void react(String partner, PrivateMessage message, String reaction, Consumer<String> failed) {
        LinkFriend friend = link.snapshot().directNamed(partner);
        if (friend == null || message.id() == null) {
            return;
        }
        String before = message.reaction();
        social.messages().react(partner, message.id(), true, reaction);
        link.direct().sendReaction(friend.id(), message.id(), reaction).thenAccept(code -> Minecraft.getInstance().execute(() -> {
            if (!"ok".equals(code)) {
                social.messages().react(partner, message.id(), true, before);
                failed.accept(LinkText.sendProblem(code));
            }
        }));
    }

    // у себя — сразу; у друга — конвертом. Конверт не ушёл — у себя всё равно удалено, говорим, что у собеседника осталось
    @Override
    public void delete(String partner, List<PrivateMessage> messages, boolean forBoth, Consumer<String> failed) {
        List<String> keys = messages.stream().map(PrivateMessage::key).toList();
        social.messages().remove(partner, keys);
        LinkFriend friend = link.snapshot().directNamed(partner);
        if (!forBoth || friend == null || !canSync(partner, messages)) {
            return;
        }
        link.direct().sendDelete(friend.id(), keys).thenAccept(code -> Minecraft.getInstance().execute(() -> {
            if (!"ok".equals(code)) {
                failed.accept("У собеседника не удалилось: " + LinkText.sendProblem(code));
            }
        }));
    }

    // свой закреп сразу; у друга не ушло — у себя оставляем, говорим
    @Override
    public void pin(String partner, PrivateMessage message, boolean pinned, boolean forBoth, Consumer<String> failed) {
        social.messages().pin(partner, message.key(), pinned);
        LinkFriend friend = link.snapshot().directNamed(partner);
        if (!forBoth || friend == null || message.id() == null) {
            return;
        }
        link.direct().sendPin(friend.id(), message.id(), pinned).thenAccept(code -> Minecraft.getInstance().execute(() -> {
            if (!"ok".equals(code)) {
                failed.accept((pinned ? "У собеседника не закрепилось: " : "У собеседника не открепилось: ")
                        + LinkText.sendProblem(code));
            }
        }));
    }

    @Override
    public boolean canSync(String partner, List<PrivateMessage> messages) {
        return !messages.isEmpty() && messages.size() <= ChatPayload.MAX_DELETE
                && link.snapshot().directNamed(partner) != null && messages.stream().allMatch(message -> message.id() != null);
    }

    @Override
    public CompletableFuture<Path> imageFile(MessageImage image) {
        return link.media().file(image);
    }

    private static String imageProblem(String code) {
        return switch (code) {
            case "image_too_big" -> "Картинка больше 5 МБ";
            case "image_format" -> "Можно только PNG, JPG или GIF";
            case "image_empty", "image_unreadable" -> "Файл не читается";
            case "quota" -> "На сегодня лимит картинок исчерпан";
            case "rate_limited" -> "Слишком часто — подожди немного";
            case "upload_failed" -> "Картинка не загрузилась на сервер";
            default -> LinkText.sendProblem(code);
        };
    }

    // строка под ником в шапке переписки; null — не друг по сети, у AFriends своя подпись
    @Override
    public String headerLine(String partner) {
        if (PrivateMessages.isGroup(partner)) {
            return groups.headerLine(partner);
        }
        LinkFriend friend = link.snapshot().directNamed(partner);
        if (friend == null) {
            return null;
        }
        boolean here = FriendManager.onlinePlayerNames().stream().anyMatch(partner::equalsIgnoreCase);
        String where = LinkText.online(friend, here) ? LinkText.line(friend, System.currentTimeMillis(), here) : "Не в сети";
        return where + " · сквозное шифрование";
    }
}
