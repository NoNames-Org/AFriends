package dev.no.name.blooddesigner.net.chat;

import dev.no.name.blooddesigner.core.MessageImage;
import dev.no.name.blooddesigner.net.model.LinkFriend;

// что из входящих конвертов уходит в игру; вызывается с потока сети
public interface InboxListener {
    void text(LinkFriend from, String text, long time);

    // конверт пришёл, но не расшифровался: подделка, сбитая сессия или сменившиеся ключи
    void unreadable(LinkFriend from, long time);

    // сообщение с id (для реакций) и, может быть, картинкой; по умолчанию — как простой текст
    default void message(LinkFriend from, String text, String id, MessageImage image, long time) {
        text(from, text, time);
    }

    // то же, но пересланное: forwardedFrom — чьё оно было
    default void message(LinkFriend from, String text, String id, MessageImage image, String forwardedFrom, long time) {
        message(from, text, id, image, time);
    }

    // то же, но ответ на сообщение reply
    default void message(LinkFriend from, String text, String id, MessageImage image, String forwardedFrom,
                         dev.no.name.blooddesigner.core.MessageReply reply, long time) {
        message(from, text, id, image, forwardedFrom, time);
    }

    // друг удалил у обоих сообщения с этими id
    default void deleted(LinkFriend from, java.util.List<String> targets, long time) {
    }

    // друг закрепил (pinned) или открепил у обоих сообщение с этим id
    default void pinned(LinkFriend from, String target, boolean pinned, long time) {
    }

    // друг поставил (reaction != null) или снял реакцию на сообщение с этим id
    default void reaction(LinkFriend from, String target, String reaction, long time) {
    }

    // сообщение в групповом чате group от участника from
    default void groupMessage(String group, LinkFriend from, String text, String id, dev.no.name.blooddesigner.core.MessageImage image,
                              dev.no.name.blooddesigner.core.MessageReply reply, long time) {
    }

    // участник from назвал или переименовал группу
    default void groupNamed(String group, LinkFriend from, String name, long time) {
    }

    // личное сообщение от BloodDesigner с портала (не от друга, без шифрования); по умолчанию никуда не идёт
    default void official(String text, java.util.List<dev.no.name.blooddesigner.core.MessageImage> images, long time) {
        official(text, time);
    }

    default void official(String text, long time) {
    }
}
