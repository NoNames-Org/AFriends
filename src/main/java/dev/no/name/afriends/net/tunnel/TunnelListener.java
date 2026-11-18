package dev.no.name.afriends.net.tunnel;

import com.google.gson.JsonObject;

// события туннеля; вызываются с потока сети, не с потока игры
public interface TunnelListener {
    // вход пройден. registered — у аккаунта уже занят ник
    void onReady(String accountId, boolean registered, long serverTime);

    // событие от сервера: msg, presence, dirty, prekeys.low, kicked
    void onEvent(String event, JsonObject data);

    // соединение закрыто; все ждавшие ответа запросы уже завершены ошибкой "offline"
    void onClosed();
}
