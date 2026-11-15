package dev.no.name.blooddesigner.net.chat;

import dev.no.name.blooddesigner.net.model.LinkFriend;

// что из входящих конвертов уходит в игру; вызывается с потока сети
public interface InboxListener {
    void text(LinkFriend from, String text, long time);

    // конверт пришёл, но не расшифровался: подделка, сбитая сессия или сменившиеся ключи
    void unreadable(LinkFriend from, long time);

    // личное сообщение от BloodDesigner с портала (не от друга, без шифрования); по умолчанию никуда не идёт
    default void official(String text, long time) {
    }
}
