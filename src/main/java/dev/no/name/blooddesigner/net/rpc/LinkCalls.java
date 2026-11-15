package dev.no.name.blooddesigner.net.rpc;

import com.google.gson.JsonObject;

import java.util.concurrent.CompletableFuture;

// Запрос к серверу сети. Ответ всегда приходит на поток сети: дальше по цепочке можно трогать сессии
@FunctionalInterface
public interface LinkCalls {
    CompletableFuture<JsonObject> request(String op, JsonObject data);
}
