package dev.no.name.afriends.net.service;

import com.google.gson.JsonObject;

import java.util.concurrent.CompletableFuture;

/** Свой профиль и настройки личных чатов в сети. Ответ — «ok» или код отказа. */
public final class ProfileGateway {
    private final LinkCalls calls;

    ProfileGateway(LinkCalls calls) {
        this.calls = calls;
    }

    // описание своего профиля; пустое — убрать
    public CompletableFuture<String> setAbout(String text) {
        JsonObject data = new JsonObject();
        data.addProperty("text", text);
        return calls.action("profile.about", data);
    }

    // кто может начать личный чат: все или только друзья
    public CompletableFuture<String> setDmFromEveryone(boolean everyone) {
        JsonObject data = new JsonObject();
        data.addProperty("value", everyone ? "all" : "friends");
        return calls.action("settings.dm", data);
    }

    // начать личный чат с игроком сети по нику; «ok» — чат есть (карточка придёт со следующим sync)
    public CompletableFuture<String> openChat(String name) {
        JsonObject data = new JsonObject();
        data.addProperty("name", name);
        return calls.action("chat.open", data);
    }

    // заблокировать / разблокировать собеседника личного чата
    public CompletableFuture<String> blockChat(String accountId, boolean on) {
        JsonObject data = new JsonObject();
        data.addProperty("id", accountId);
        data.addProperty("on", on);
        return calls.action("chat.block", data);
    }
}
