package dev.no.name.afriends.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/** Закреплённые сообщения: ключ чата → ключи сообщений в порядке закрепления. */
final class PinnedMessages {
    // старые версии этот ключ пропустят как битую переписку
    static final String JSON_KEY = "$pins";

    private final Map<String, List<String>> pins = new HashMap<>();

    boolean isPinned(String chat, String messageKey) {
        return pins.getOrDefault(chat, List.of()).contains(messageKey);
    }

    List<String> keys(String chat) {
        return List.copyOf(pins.getOrDefault(chat, List.of()));
    }

    void pin(String chat, String messageKey) {
        List<String> chatPins = pins.computeIfAbsent(chat, key -> new ArrayList<>());
        chatPins.remove(messageKey);
        chatPins.add(messageKey);
    }

    void unpin(String chat, String messageKey) {
        pins.computeIfAbsent(chat, key -> new ArrayList<>()).remove(messageKey);
    }

    void unpinAll(String chat, Collection<String> messageKeys) {
        List<String> chatPins = pins.get(chat);
        if (chatPins != null) {
            chatPins.removeAll(messageKeys);
        }
    }

    void remove(String chat) {
        pins.remove(chat);
    }

    void clear() {
        pins.clear();
    }

    void writeTo(JsonObject json, UnaryOperator<String> label) {
        JsonObject pinsJson = new JsonObject();
        pins.forEach((key, keys) -> {
            if (!keys.isEmpty()) {
                JsonArray array = new JsonArray();
                keys.forEach(array::add);
                pinsJson.add(label.apply(key), array);
            }
        });
        json.add(JSON_KEY, pinsJson);
    }

    void read(JsonElement json) {
        if (!json.isJsonObject()) {
            return;
        }
        json.getAsJsonObject().entrySet().forEach(entry -> {
            if (entry.getValue().isJsonArray()) {
                List<String> keys = new ArrayList<>();
                entry.getValue().getAsJsonArray().forEach(item -> keys.add(item.getAsString()));
                pins.put(ChatId.storageKey(entry.getKey()), keys);
            }
        });
    }
}
