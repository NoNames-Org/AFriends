package dev.no.name.afriends.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashSet;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Закреплённые и архивные чаты. Чат либо закреплён, либо в архиве: одно снимает другое.
 * Чат AFriends не закрепляется и вместе с «Избранным» не уходит в архив. Ключи — ключи хранения
 */
final class ChatOrganizer {
    static final String PINNED_KEY = "$pinned";
    // старые версии этот ключ пропустят как битую переписку
    static final String ARCHIVED_KEY = "$archived";

    private final Set<String> pinned = new HashSet<>();
    private final Set<String> archived = new HashSet<>();

    static boolean owns(String jsonKey) {
        return PINNED_KEY.equals(jsonKey) || ARCHIVED_KEY.equals(jsonKey);
    }

    boolean isPinned(String key) {
        return pinned.contains(key);
    }

    boolean isArchived(String key) {
        return archived.contains(key);
    }

    Set<String> archived() {
        return Set.copyOf(archived);
    }

    /** false — этот чат закреплять нельзя. */
    boolean togglePin(ChatId chat) {
        if (chat instanceof ChatId.Official) {
            return false;
        }
        if (!pinned.remove(chat.key())) {
            pinned.add(chat.key());
            archived.remove(chat.key());
        }
        return true;
    }

    /** false — этот чат в архив не убирается. */
    boolean toggleArchived(ChatId chat) {
        if (chat instanceof ChatId.Official || chat instanceof ChatId.Notes) {
            return false;
        }
        if (!archived.remove(chat.key())) {
            archived.add(chat.key());
            pinned.remove(chat.key());
        }
        return true;
    }

    void unarchive(String key) {
        archived.remove(key);
    }

    void remove(String key) {
        pinned.remove(key);
        archived.remove(key);
    }

    void clear() {
        pinned.clear();
        archived.clear();
    }

    void writeTo(JsonObject json, UnaryOperator<String> label) {
        json.add(PINNED_KEY, labels(pinned, label));
        json.add(ARCHIVED_KEY, labels(archived, label));
    }

    void read(String jsonKey, JsonElement json) {
        if (!json.isJsonArray()) {
            return;
        }
        Set<String> target = PINNED_KEY.equals(jsonKey) ? pinned : archived;
        json.getAsJsonArray().forEach(name -> target.add(ChatId.storageKey(name.getAsString())));
    }

    private static JsonArray labels(Set<String> keys, UnaryOperator<String> label) {
        JsonArray array = new JsonArray();
        keys.forEach(key -> array.add(label.apply(key)));
        return array;
    }
}
