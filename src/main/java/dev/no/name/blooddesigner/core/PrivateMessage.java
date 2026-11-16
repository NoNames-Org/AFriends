package dev.no.name.blooddesigner.core;

import java.util.List;

/**
 * Сообщение переписки. id есть у сообщений друзей по сети: по нему ставятся реакции, ответы, закреп и удаление у обоих.
 * У сообщений через /msg сервера и старых версий мода id нет — у себя их узнают по key().
 * images — картинки и гифки; где они стоят в тексте, решают метки [img] (см. MessageContent), остальные — сверху.
 * reaction / partnerReaction — своя реакция и реакция собеседника. forwardedFrom — чьё это было сообщение,
 * если его переслали (null — не пересланное). reply — на какое сообщение это ответ (null — не ответ).
 * author — кто написал в групповом чате (null — личка или своё)
 */
public record PrivateMessage(boolean fromMe, String text, long time, String id, List<MessageImage> images,
                             String reaction, String partnerReaction, String forwardedFrom, MessageReply reply, String author) {

    public PrivateMessage {
        images = images == null ? List.of() : List.copyOf(images);
    }

    public PrivateMessage(boolean fromMe, String text, long time, String id, List<MessageImage> images,
                          String reaction, String partnerReaction, String forwardedFrom, MessageReply reply) {
        this(fromMe, text, time, id, images, reaction, partnerReaction, forwardedFrom, reply, null);
    }

    public PrivateMessage(boolean fromMe, String text, long time) {
        this(fromMe, text, time, null, List.of(), null, null, null, null);
    }

    public PrivateMessage(boolean fromMe, String text, long time, String id, MessageImage image) {
        this(fromMe, text, time, id, image == null ? List.of() : List.of(image), null, null, null, null);
    }

    public PrivateMessage(boolean fromMe, String text, long time, String id, List<MessageImage> images,
                          String reaction, String partnerReaction) {
        this(fromMe, text, time, id, images, reaction, partnerReaction, null, null);
    }

    public PrivateMessage(boolean fromMe, String text, long time, String id, List<MessageImage> images,
                          String reaction, String partnerReaction, String forwardedFrom) {
        this(fromMe, text, time, id, images, reaction, partnerReaction, forwardedFrom, null);
    }

    // первая картинка: в личке у игроков она одна
    public MessageImage image() {
        return images.isEmpty() ? null : images.get(0);
    }

    public boolean hasReactions() {
        return reaction != null || partnerReaction != null;
    }

    /**
     * Как сообщение узнаётся у себя: по id, а без него — по времени и стороне. Ответы, закреп,
     * выделение и удаление ищут по нему
     */
    public String key() {
        return id != null ? id : "t" + time + (fromMe ? "m" : "p");
    }

    // заметке без id выдаётся свой: две заметки в одну миллисекунду (переслали пачкой) иначе не различить
    public PrivateMessage withId(String newId) {
        return new PrivateMessage(fromMe, text, time, newId, images, reaction, partnerReaction, forwardedFrom, reply, author);
    }

    // mine — своя реакция или собеседника; null снимает
    public PrivateMessage withReaction(boolean mine, String value) {
        return mine ? new PrivateMessage(fromMe, text, time, id, images, value, partnerReaction, forwardedFrom, reply, author)
                : new PrivateMessage(fromMe, text, time, id, images, reaction, value, forwardedFrom, reply, author);
    }
}
