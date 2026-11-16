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

    // друг поставил (reaction != null) или снял реакцию на сообщение с этим id
    default void reaction(LinkFriend from, String target, String reaction, long time) {
    }

    // личное сообщение от BloodDesigner с портала (не от друга, без шифрования); по умолчанию никуда не идёт
    default void official(String text, java.util.List<dev.no.name.blooddesigner.core.MessageImage> images, long time) {
        official(text, time);
    }

    default void official(String text, long time) {
    }
}
