package dev.no.name.afriends.net.model;

// состояние связи с сервером сети, как его видит интерфейс
public enum Connection {
    // сеть выключена в настройках или адрес сервера не задан
    DISABLED,
    CONNECTING,
    ONLINE,
    // нет связи: интернет, блокировка, сервер лежит
    OFFLINE,
    // ник занят другим аккаунтом сети (подтверждённым через Mojang или пришедшим раньше)
    NAME_TAKEN
}
