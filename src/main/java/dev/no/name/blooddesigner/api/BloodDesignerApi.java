package dev.no.name.blooddesigner.api;

// что дополнение может встроить в BloodDesigner. Всё необязательно: без дополнений окна работают как обычно
public interface BloodDesignerApi {
    // проверка перед отправкой сообщения в ЛС
    void addSendCheck(SendCheck check);

    // что прятать во время трансляции
    void setPrivacy(Privacy privacy);

    // строка под ником друга и онлайн
    void addFriendStatus(FriendStatus status);

    // свой пункт в меню друга
    void addFriendAction(FriendAction action);

    // друга добавили или удалили
    void addFriendListener(FriendListener listener);

    // заявки в друзья сверху списка
    void addFriendRequests(FriendRequests requests);

    // свой статус под ником в шапке друзей
    void setProfileStatus(ProfileStatus status);

    // своя карточка вместо текста сообщения
    void addMessageCard(MessageCard card);

    // кнопка у поля ввода в ЛС
    void addComposeAction(ComposeAction action);

    // друзья на текущем сервере
    Friends friends();

    // позвать в пати
    void inviteToParty(String name);
}
