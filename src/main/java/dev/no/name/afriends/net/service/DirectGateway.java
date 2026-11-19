package dev.no.name.afriends.net.service;

import dev.no.name.afriends.core.MessageImage;
import dev.no.name.afriends.net.chat.ChatPayload;
import dev.no.name.afriends.net.media.PreparedImage;
import dev.no.name.afriends.net.tunnel.TunnelError;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Личная переписка по сети: текст, картинки и служебные сообщения о сообщениях. */
public final class DirectGateway {
    private final LinkCalls calls;

    DirectGateway(LinkCalls calls) {
        this.calls = calls;
    }

    // «ok» или код причины
    public CompletableFuture<String> sendText(String friendId, OutgoingText message) {
        return calls.send(friendId, new ChatPayload.Text(message.text(), message.id(), null, message.forwardedFrom(),
                message.reply()));
    }

    // Картинка или голосовое: шифруем своим ключом, заливаем, ключ и ссылку — сквозным сообщением.
    // Готово — описание картинки (для своей переписки); отказ — исключение с кодом
    public CompletableFuture<MessageImage> sendImage(String friendId, PreparedImage prepared, OutgoingText caption) {
        return calls.upload(prepared).thenCompose(image -> calls.send(friendId, new ChatPayload.Text(caption.text(),
                        caption.id(), image, caption.forwardedFrom(), caption.reply()))
                .thenCompose(code -> "ok".equals(code) ? CompletableFuture.completedFuture(image)
                        : CompletableFuture.failedFuture(new TunnelError(code))));
    }

    // отметки «доставлено» / «прочитано» автору сообщений
    public CompletableFuture<String> sendReceipt(String peerId, List<String> targets, boolean read) {
        return calls.send(peerId, new ChatPayload.Receipt(targets, read));
    }

    // удалить у друга сообщения с этими id
    public CompletableFuture<String> sendDelete(String friendId, List<String> targets) {
        return calls.send(friendId, new ChatPayload.Delete(targets));
    }

    // закрепить или открепить у друга сообщение с этим id
    public CompletableFuture<String> sendPin(String friendId, String target, boolean pinned) {
        return calls.send(friendId, new ChatPayload.Pin(target, pinned));
    }

    // реакция на сообщение с этим id; reaction == null — снять свою
    public CompletableFuture<String> sendReaction(String friendId, String target, String reaction) {
        return calls.send(friendId, new ChatPayload.Reaction(target, reaction));
    }
}
