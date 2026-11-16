package dev.no.name.blooddesigner.core;

import java.util.List;
import java.util.function.Consumer;

/**
 * Групповые чаты для окон: окна не знают про сеть. Чат группы — ключ {@link PrivateMessages#groupKey}.
 * problem получает текст причины, если не вышло (на потоке игры)
 */
public interface GroupChats {
    int MAX_TITLE = 32;

    // сеть подключена: без неё группы не создать и не изменить
    boolean available();

    // друзья по сети, которых можно позвать в группу
    List<String> friends();

    // участники кроме себя
    List<String> members(String chat);

    boolean owned(String chat);

    // done получает ключ чата новой группы
    void create(List<String> friendNames, String title, Consumer<String> done, Consumer<String> problem);

    void add(String chat, String friendName, Consumer<String> problem);

    void rename(String chat, String title, Consumer<String> problem);

    void leave(String chat, Consumer<String> problem);

    void kick(String chat, String memberName, Consumer<String> problem);
}
