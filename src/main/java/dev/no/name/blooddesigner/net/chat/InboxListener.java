package dev.no.name.blooddesigner.net.chat;

import dev.no.name.blooddesigner.net.model.LinkFriend;

// что из входящих конвертов уходит в игру; вызывается с потока сети
public interface InboxListener {
    void text(LinkFriend from, String text, long time);

    // конверт пришёл, но не расшифровался: подделка, сбитая сессия или сменившиеся ключи
    void unreadable(LinkFriend from, long time);
}
