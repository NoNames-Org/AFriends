package dev.no.name.blooddesigner.net.game;

import dev.no.name.blooddesigner.addon.Addons;
import dev.no.name.blooddesigner.core.MessageRoute;
import dev.no.name.blooddesigner.core.MessageImage;
import dev.no.name.blooddesigner.core.PrivateMessage;
import dev.no.name.blooddesigner.core.PrivateMessages;
import dev.no.name.blooddesigner.core.SocialData;
import dev.no.name.blooddesigner.net.chat.MessageIds;
import dev.no.name.blooddesigner.net.media.PreparedImage;
import dev.no.name.blooddesigner.net.model.Connection;
import dev.no.name.blooddesigner.net.rpc.Failures;
import dev.no.name.blooddesigner.net.model.LinkFriend;
import dev.no.name.blooddesigner.net.model.LinkSnapshot;
import dev.no.name.blooddesigner.net.service.LinkService;
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
    private final SocialData social;
    private final LinkService link;
    private final OfficialRoute official;
    private final NotesRoute notes;

    DirectMessages(SocialData social, LinkService link) {
        this.social = social;
        this.link = link;
        this.official = new OfficialRoute(social, link);
        this.notes = new NotesRoute(social, link.media());
    }

    // друг по сети: пишется через сервер сети, и неважно, на каком сервере Minecraft он сейчас
    @Override
    public boolean viaNetwork(String partner) {
        return partner != null && link.snapshot().friendNamed(partner) != null;
    }

    // Отправить. Пусто — ушло в обработку; иначе причина, почему не ушло сразу.
    // Если сеть откажет позже, причина придёт в failed, а сообщение будет помечено неотправленным
    @Override
    public Optional<String> send(String partner, String text, Consumer<String> failed) {
        return send(partner, text, null, failed);
    }

    @Override
    public Optional<String> send(String partner, String text, String forwardedFrom, Consumer<String> failed) {
        LinkSnapshot state = link.snapshot();
        if (PrivateMessages.NOTES.equals(partner)) {
            return notes.send(text, forwardedFrom);
        }
        // BloodDesigner: админам мода через сервер сети, на портал
        if (PrivateMessages.isOfficial(partner)) {
            return official.send(text, forwardedFrom, failed);
        }
        LinkFriend friend = state.friendNamed(partner);
        if (friend != null) {
            if (state.connection() != Connection.ONLINE) {
                return Optional.of(LinkText.sendProblem("offline"));
            }
            String id = MessageIds.next();
            link.sendText(friend.id(), text, id, forwardedFrom).thenAccept(code -> Minecraft.getInstance().execute(() -> {
                long now = System.currentTimeMillis();
                if ("ok".equals(code)) {
                    social.messages().add(partner, new PrivateMessage(true, text, now, id, List.of(), null, null, forwardedFrom));
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
        Optional<String> problem = Addons.get().sendProblem(partner, command);
        if (problem.isEmpty()) {
            connection.sendCommand(command);
        }
        return problem;
    }

    // картинки: другу по сети, админам в BloodDesigner и себе в «Избранное»; через /msg — нет
    @Override
    public boolean supportsMedia(String partner) {
        return partner != null && (PrivateMessages.NOTES.equals(partner) || PrivateMessages.isOfficial(partner)
                || link.snapshot().friendNamed(partner) != null);
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
        if (PrivateMessages.NOTES.equals(partner)) {
            return notes.sendImage(file, caption, forwardedFrom, failed);
        }
        if (PrivateMessages.isOfficial(partner)) {
            return official.sendImage(file, caption, forwardedFrom, failed);
        }
        LinkSnapshot state = link.snapshot();
        LinkFriend friend = state.friendNamed(partner);
        if (friend == null) {
            return Optional.of("Картинки можно отправлять только друзьям по сети");
        }
        if (state.connection() != Connection.ONLINE) {
            return Optional.of(LinkText.sendProblem("offline"));
        }
        String id = MessageIds.next();
        CompletableFuture.supplyAsync(() -> PreparedImage.read(file))
                .thenCompose(prepared -> link.sendImage(friend.id(), prepared, id, caption, forwardedFrom))
                .whenComplete((image, error) -> Minecraft.getInstance().execute(() -> {
                    if (error == null) {
                        social.messages().add(partner, new PrivateMessage(true, caption, System.currentTimeMillis(), id,
                                List.of(image), null, null, forwardedFrom));
                    } else {
                        failed.accept(imageProblem(Failures.codeOf(error)));
                    }
                }));
        return Optional.empty();
    }

    // своя реакция сразу видна у себя; не ушла — откатываем
    @Override
    public void react(String partner, PrivateMessage message, String reaction, Consumer<String> failed) {
        LinkFriend friend = link.snapshot().friendNamed(partner);
        if (friend == null || message.id() == null) {
            return;
        }
        String before = message.reaction();
        social.messages().react(partner, message.id(), true, reaction);
        link.sendReaction(friend.id(), message.id(), reaction).thenAccept(code -> Minecraft.getInstance().execute(() -> {
            if (!"ok".equals(code)) {
                social.messages().react(partner, message.id(), true, before);
                failed.accept(LinkText.sendProblem(code));
            }
        }));
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

    // строка под ником в шапке переписки; null — не друг по сети, у BloodDesigner своя подпись
    @Override
    public String headerLine(String partner) {
        LinkFriend friend = link.snapshot().friendNamed(partner);
        if (friend == null) {
            return null;
        }
        String where = friend.online() ? LinkText.line(friend, System.currentTimeMillis()) : "Не в сети — прочитает при входе";
        return where + " · сквозное шифрование";
    }
}
