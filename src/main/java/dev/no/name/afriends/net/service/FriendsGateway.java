package dev.no.name.afriends.net.service;

import com.google.gson.JsonObject;

import java.util.concurrent.CompletableFuture;

/** Заявки в друзья и удаление из друзей в сети. Ответ — «ok» или код отказа. */
public final class FriendsGateway {
    private final LinkCalls calls;

    FriendsGateway(LinkCalls calls) {
        this.calls = calls;
    }

    public CompletableFuture<String> request(String name) {
        JsonObject data = new JsonObject();
        data.addProperty("name", name);
        return calls.action("friends.request", data);
    }

    public CompletableFuture<String> respond(String friendId, boolean accept) {
        JsonObject data = new JsonObject();
        data.addProperty("id", friendId);
        data.addProperty("accept", accept);
        return calls.action("friends.respond", data);
    }

    public CompletableFuture<String> remove(String friendId) {
        JsonObject data = new JsonObject();
        data.addProperty("id", friendId);
        return calls.action("friends.remove", data);
    }
}
