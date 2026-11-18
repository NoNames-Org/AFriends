package dev.no.name.afriends.core;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

// Куда уходит ЛС: окна не знают, сеть это или /msg сервера. Всё на потоке игры
public interface MessageRoute {
    // пусто — ушло в обработку; иначе причина, почему не ушло. Поздний отказ сети приходит в failed
    Optional<String> send(String partner, String text, Consumer<String> failed);

    // пересланное: forwardedFrom — чьё сообщение. Где пометку не передать, она идёт строкой в тексте
    default Optional<String> send(String partner, String text, String forwardedFrom, Consumer<String> failed) {
        return send(partner, forwardedFrom == null ? text : forwardedPrefix(forwardedFrom) + text, failed);
    }

    // ответ на сообщение reply; где ответ не передать (/msg сервера, AFriends), уходит просто текст
    default Optional<String> send(String partner, String text, String forwardedFrom, MessageReply reply, Consumer<String> failed) {
        return send(partner, text, forwardedFrom, failed);
    }

    static String forwardedPrefix(String forwardedFrom) {
        return "Переслано от " + forwardedFrom + ":\n";
    }

    // групповые чаты; null — сети нет вовсе
    default GroupChats groups() {
        return null;
    }

    // профиль и личные чаты без дружбы; null — сети нет вовсе
    default ProfileRoute profile() {
        return null;
    }

    // игрок видит эти сообщения чата: собеседникам по сети уходит «прочитано»
    default void markRead(String chat, List<PrivateMessage> seen) {
    }

    // друг по сети: писать можно, даже если он на другом сервере
    boolean viaNetwork(String partner);

    // строка под ником в шапке переписки; null — своя подпись AFriends
    String headerLine(String partner);

    // картинки и реакции ходят только между друзьями по сети: через /msg сервера их не передать
    default boolean supportsMedia(String partner) {
        return false;
    }

    // картинка или гифка с диска и подпись под ней; пусто — ушла в обработку, иначе причина. Поздний отказ — в failed
    default Optional<String> sendImage(String partner, Path file, String caption, Consumer<String> failed) {
        return Optional.of("Картинки можно отправлять только друзьям по сети");
    }

    default Optional<String> sendImage(String partner, Path file, String caption, String forwardedFrom, Consumer<String> failed) {
        return sendImage(partner, file, caption, failed);
    }

    default Optional<String> sendImage(String partner, Path file, String caption, String forwardedFrom, MessageReply reply,
                                       Consumer<String> failed) {
        return sendImage(partner, file, caption, forwardedFrom, failed);
    }

    // голосовое другу по сети: data — файл VoiceClip. Пусто — ушло в обработку, иначе причина
    default Optional<String> sendVoice(String partner, byte[] data, int centiseconds, MessageReply reply, Consumer<String> failed) {
        return Optional.of("Голосовые можно отправлять только друзьям по сети");
    }

    // удалить сообщения у себя, а forBoth — ещё и у собеседника (только друг по сети и сообщения с id)
    default void delete(String partner, List<PrivateMessage> messages, boolean forBoth, Consumer<String> failed) {
    }

    // закрепить или открепить сообщение у себя, а forBoth — ещё и у собеседника
    default void pin(String partner, PrivateMessage message, boolean pinned, boolean forBoth, Consumer<String> failed) {
    }

    // можно ли удалить или закрепить у обоих: друг по сети и у всех сообщений есть id
    default boolean canSync(String partner, List<PrivateMessage> messages) {
        return false;
    }

    // пересланные картинки в «Избранное»: они уже в кеше, заново не грузятся
    default void keepNote(String text, List<MessageImage> images, String forwardedFrom) {
    }

    // поставить свою реакцию на сообщение (reaction == null — снять)
    default void react(String partner, PrivateMessage message, String reaction, Consumer<String> failed) {
    }

    // файл картинки на диске: из кеша или скачанный и расшифрованный
    default CompletableFuture<Path> imageFile(MessageImage image) {
        return CompletableFuture.failedFuture(new IllegalStateException("нет сети"));
    }
}
