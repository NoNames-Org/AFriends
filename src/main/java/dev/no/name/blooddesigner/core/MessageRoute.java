package dev.no.name.blooddesigner.core;

import java.nio.file.Path;
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

    static String forwardedPrefix(String forwardedFrom) {
        return "Переслано от " + forwardedFrom + ":\n";
    }

    // друг по сети: писать можно, даже если он на другом сервере
    boolean viaNetwork(String partner);

    // строка под ником в шапке переписки; null — своя подпись BloodDesigner
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

    // пересланные картинки в «Избранное»: они уже в кеше, заново не грузятся
    default void keepNote(String text, java.util.List<MessageImage> images, String forwardedFrom) {
    }

    // поставить свою реакцию на сообщение (reaction == null — снять)
    default void react(String partner, PrivateMessage message, String reaction, Consumer<String> failed) {
    }

    // файл картинки на диске: из кеша или скачанный и расшифрованный
    default CompletableFuture<Path> imageFile(MessageImage image) {
        return CompletableFuture.failedFuture(new IllegalStateException("нет сети"));
    }
}
