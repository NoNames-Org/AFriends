package dev.no.name.afriends.core;

import java.util.function.Consumer;

/**
 * Профиль и личные чаты через сеть AFriends: своё описание, кто может писать, переписка с игроком по нику
 * без дружбы и блокировка. Окна не знают про сеть. problem получает текст причины (на потоке игры)
 */
public interface ProfileRoute {
    int MAX_ABOUT = 140;

    // сеть подключена и синхронизирована
    boolean available();

    String about();

    void setAbout(String text, Consumer<String> problem);

    boolean dmFromEveryone();

    void setDmFromEveryone(boolean everyone, Consumer<String> problem);

    // описание профиля игрока (друга, участника общей группы, собеседника); пусто — нет
    String aboutOf(String name);

    // начать личный чат с игроком сети по нику. done — чат есть, можно писать по сети;
    // notInNetwork — такого игрока в сети нет (тогда пишем, как раньше, /msg этого сервера)
    void startChat(String name, Runnable done, Runnable notInNetwork, Consumer<String> problem);

    boolean isBlocked(String name);

    void setBlocked(String name, boolean blocked, Consumer<String> problem);
}
