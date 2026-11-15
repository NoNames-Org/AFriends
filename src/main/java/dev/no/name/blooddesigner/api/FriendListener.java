package dev.no.name.blooddesigner.api;

import java.util.function.Consumer;

// друга добавили или удалили: из панели, меню чата или /friend
public interface FriendListener {
    // notice — показать игроку короткую подпись в панели друзей
    void added(String name, Consumer<String> notice);

    void removed(String name);
}
