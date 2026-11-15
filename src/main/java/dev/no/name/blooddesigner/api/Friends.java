package dev.no.name.blooddesigner.api;

import java.util.List;

// друзья на текущем сервере. Вне сервера список пустой и не меняется
public interface Friends {
    List<String> names();

    boolean contains(String name);

    // true — добавили; слушатели FriendListener не вызываются
    boolean add(String name);
}
