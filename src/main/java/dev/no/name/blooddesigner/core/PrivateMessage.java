package dev.no.name.blooddesigner.core;

/**
 * Сообщение переписки. id есть у сообщений друзей по сети: по нему ставятся реакции.
 * У сообщений через /msg сервера, заметок и старых версий мода id нет — на них реакцию не поставить.
 * image — картинка или гифка вместо текста; reaction / partnerReaction — своя реакция и реакция собеседника.
 */
public record PrivateMessage(boolean fromMe, String text, long time, String id, MessageImage image,
                             String reaction, String partnerReaction) {

    public PrivateMessage(boolean fromMe, String text, long time) {
        this(fromMe, text, time, null, null, null, null);
    }

    public PrivateMessage(boolean fromMe, String text, long time, String id, MessageImage image) {
        this(fromMe, text, time, id, image, null, null);
    }

    public boolean hasReactions() {
        return reaction != null || partnerReaction != null;
    }

    // mine — своя реакция или собеседника; null снимает
    public PrivateMessage withReaction(boolean mine, String value) {
        return mine ? new PrivateMessage(fromMe, text, time, id, image, value, partnerReaction)
                : new PrivateMessage(fromMe, text, time, id, image, reaction, value);
    }
}
