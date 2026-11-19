package dev.no.name.afriends.net.service;

import dev.no.name.afriends.core.MessageReply;

/**
 * Текст, который уходит собеседнику, — вместо цепочки перегрузок sendText(…).
 *
 * @param id            номер сообщения для реакций, ответов и удаления; null — без номера
 * @param forwardedFrom чьё сообщение пересылаем; null — своё
 * @param reply         на какое сообщение ответ; null — не ответ
 */
public record OutgoingText(String text, String id, String forwardedFrom, MessageReply reply) {
    /** Сообщение без номера: на него не поставить реакцию (для проверок сети). */
    public static OutgoingText of(String text) {
        return new OutgoingText(text, null, null, null);
    }

    public static OutgoingText of(String text, String id) {
        return new OutgoingText(text, id, null, null);
    }
}
