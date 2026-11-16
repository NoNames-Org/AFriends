package dev.no.name.blooddesigner.core;

import java.util.List;

/**
 * Сообщение переписки. id есть у сообщений друзей по сети: по нему ставятся реакции.
 * У сообщений через /msg сервера, заметок и старых версий мода id нет — на них реакцию не поставить.
 * images — картинки и гифки; где они стоят в тексте, решают метки [img] (см. MessageContent), остальные — сверху.
 * reaction / partnerReaction — своя реакция и реакция собеседника.
 */
public record PrivateMessage(boolean fromMe, String text, long time, String id, List<MessageImage> images,
                             String reaction, String partnerReaction) {

    public PrivateMessage {
        images = images == null ? List.of() : List.copyOf(images);
    }

    public PrivateMessage(boolean fromMe, String text, long time) {
        this(fromMe, text, time, null, List.of(), null, null);
    }

    public PrivateMessage(boolean fromMe, String text, long time, String id, MessageImage image) {
        this(fromMe, text, time, id, image == null ? List.of() : List.of(image), null, null);
    }

    // первая картинка: в личке у игроков она одна
    public MessageImage image() {
        return images.isEmpty() ? null : images.get(0);
    }

    public boolean hasReactions() {
        return reaction != null || partnerReaction != null;
    }

    // mine — своя реакция или собеседника; null снимает
    public PrivateMessage withReaction(boolean mine, String value) {
        return mine ? new PrivateMessage(fromMe, text, time, id, images, value, partnerReaction)
                : new PrivateMessage(fromMe, text, time, id, images, reaction, value);
    }
}
