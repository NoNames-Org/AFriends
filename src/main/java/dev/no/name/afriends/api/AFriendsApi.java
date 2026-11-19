package dev.no.name.afriends.api;

import java.util.Optional;

/**
 * Что аддон может встроить в AFriends. Всё необязательно: без аддонов окна работают как обычно.
 * Методы регистрации зовутся из {@link AFriendsAddon#register}; остальные — на потоке игры.
 * Ошибка в коде аддона пишется в лог один раз и не роняет игру.
 */
public interface AFriendsApi {
    /** Версия контракта. Мажорная растёт, только когда что-то ломается; новые методы приходят в минорной. */
    int VERSION_MAJOR = 2;
    int VERSION_MINOR = 1;

    // ---- окна ----

    /** Своя кнопка в панели справа от чата и своя панель рядом (как «Друзья»). Встаёт после «Друзей». */
    void addRailPanel(RailPanel panel);

    /** Своя категория в настройках AFriends, после встроенных. */
    void addSettingsCategory(SettingsCategory category);

    /** Пункт в меню друга и в меню переписки с ним; встаёт над встроенными пунктами. */
    void addFriendAction(FriendAction action);

    /** Кнопка у поля ввода в ЛС. */
    void addComposeAction(ComposeAction action);

    /** Своя карточка вместо текста сообщения. */
    void addMessageCard(MessageCard card);

    /** Строка под ником друга, онлайн и доступность. */
    void addFriendStatus(FriendStatus status);

    /** Заявки в друзья сверху списка. */
    void addFriendRequests(FriendRequests requests);

    /** Свой статус под ником в шапке друзей. */
    void setProfileStatus(ProfileStatus status);

    /** Что прятать во время трансляции. */
    void setPrivacy(Privacy privacy);

    // ---- события ----

    /** Проверка перед отправкой сообщения в ЛС. */
    void addSendCheck(SendCheck check);

    /** Друга добавили или удалили. */
    void addFriendListener(FriendListener listener);

    // ---- данные и действия ----

    /** Друзья игрока. */
    Friends friends();

    /**
     * ЛС от имени игрока: другу по сети — сквозным шифрованием, остальным — /msg сервера.
     * Пусто — ушло; иначе причина.
     */
    Optional<String> sendMessage(String partner, String text);

    /** С этим игроком переписка идёт через сеть AFriends, а не через сервер Minecraft. */
    boolean viaNetwork(String partner);
}
