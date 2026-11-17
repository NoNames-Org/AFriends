package dev.no.name.blooddesigner.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class SocialData {
    private static final String LEGACY_KEY = "$unassigned";

    public record Scope(FriendManager friends, IgnoreList ignored, PrivateMessages messages) {
        boolean isEmpty() {
            return friends.size() == 0 && ignored.all().isEmpty() && messages.isEmpty();
        }
    }

    private final Map<String, Scope> scopes = new LinkedHashMap<>();

    private Scope legacy;
    private Runnable onMessagesChange = () -> { };
    public void setOnMessagesChange(Runnable listener) {
        onMessagesChange = listener;
        scopes.values().forEach(scope -> scope.messages().setOnChange(listener));
        if (legacy != null) {
            legacy.messages().setOnChange(listener);
        }
    }

    public void trimMessages() {
        scopes.values().forEach(scope -> scope.messages().trimAll());
        if (legacy != null) {
            legacy.messages().trimAll();
        }
    }

    public Scope current() {
        return scope(ServerScope.current());
    }

    public FriendManager friends() {
        return current().friends();
    }

    public IgnoreList ignored() {
        return current().ignored();
    }

    public PrivateMessages messages() {
        return current().messages();
    }

    // реакция по сети: сообщение с этим id ищем во всех серверах — реакция может прийти и из главного меню
    public boolean react(String partner, String messageId, boolean mine, String reaction) {
        for (Scope scope : scopes.values()) {
            if (scope.messages().react(partner, messageId, mine, reaction)) {
                return true;
            }
        }
        return legacy != null && legacy.messages().react(partner, messageId, mine, reaction);
    }

    // голос в опросе группы по сети: ищем во всех серверах, как реакцию. voter null — свой
    public boolean vote(String chat, String messageId, String voter, int choice) {
        for (PrivateMessages messages : allMessages()) {
            if (messages.vote(chat, messageId, voter, choice)) {
                return true;
            }
        }
        return false;
    }

    // собеседник удалил сообщения у обоих: ищем во всех серверах, как реакцию
    public void removeEverywhere(String partner, Collection<String> messageIds) {
        allMessages().forEach(messages -> messages.remove(partner, messageIds));
    }

    // собеседник закрепил или открепил сообщение у обоих
    public void pinEverywhere(String partner, String messageId, boolean pinned) {
        allMessages().forEach(messages -> messages.pin(partner, messageId, pinned));
    }

    private List<PrivateMessages> allMessages() {
        List<PrivateMessages> all = new ArrayList<>();
        scopes.values().forEach(scope -> all.add(scope.messages()));
        if (legacy != null) {
            all.add(legacy.messages());
        }
        return all;
    }

    // друг удалил нас в сети: на каждом сервере он больше не друг. true — где-то был
    public boolean removeFriendEverywhere(String name) {
        boolean removed = false;
        for (Scope scope : scopes.values()) {
            removed |= scope.friends().remove(name);
        }
        if (legacy != null) {
            removed |= legacy.friends().remove(name);
        }
        return removed;
    }

    public void tick() {
        if (!ServerScope.NONE.equals(ServerScope.current())) {
            friends().tick();
        }
    }

    private Scope scope(String key) {
        Scope existing = scopes.get(key);
        if (existing != null) {
            return existing;
        }
        Scope created = legacy != null && !ServerScope.NONE.equals(key) ? legacy : newScope();
        if (created == legacy) {
            legacy = null;
            onMessagesChange.run();
        }
        scopes.put(key, created);
        return created;
    }

    private Scope newScope() {
        Scope scope = new Scope(new FriendManager(), new IgnoreList(), new PrivateMessages());
        scope.messages().setOnChange(onMessagesChange);
        return scope;
    }

    private Scope legacyScope() {
        if (legacy == null) {
            legacy = newScope();
        }
        return legacy;
    }

    public JsonElement friendsJson() {
        return write(scope -> scope.friends().toJson());
    }

    public void friendsFromJson(JsonElement json) {
        read(json, JsonElement::isJsonArray, (scope, data) -> scope.friends().fromJson(data));
    }

    public JsonElement ignoredJson() {
        return write(scope -> scope.ignored().toJson());
    }

    public void ignoredFromJson(JsonElement json) {
        read(json, JsonElement::isJsonArray, (scope, data) -> scope.ignored().fromJson(data));
    }

    public JsonElement messagesJson() {
        return write(scope -> scope.messages().toJson());
    }

    public void messagesFromJson(JsonElement json) {
        read(json, SocialData::isLegacyMessages, (scope, data) -> scope.messages().fromJson(data));
    }

    private static boolean isLegacyMessages(JsonElement json) {
        if (!json.isJsonObject()) {
            return false;
        }
        return json.getAsJsonObject().entrySet().stream().anyMatch(entry -> entry.getValue().isJsonArray());
    }

    private JsonElement write(Function<Scope, JsonElement> writer) {
        JsonObject json = new JsonObject();
        scopes.forEach((key, scope) -> {
            if (!ServerScope.NONE.equals(key) && !scope.isEmpty()) {
                json.add(key, writer.apply(scope));
            }
        });

        if (legacy != null && !legacy.isEmpty()) {
            json.add(LEGACY_KEY, writer.apply(legacy));
        }
        return json;
    }

    private void read(JsonElement json, Predicate<JsonElement> isLegacy, BiConsumer<Scope, JsonElement> reader) {
        if (isLegacy.test(json)) {
            reader.accept(legacyScope(), json);
            return;
        }
        if (!json.isJsonObject()) {
            return;
        }
        json.getAsJsonObject().entrySet().forEach(entry -> {
            Scope scope = LEGACY_KEY.equals(entry.getKey()) ? legacyScope() : scopes.computeIfAbsent(entry.getKey(), key -> newScope());
            reader.accept(scope, entry.getValue());
        });
    }
}
