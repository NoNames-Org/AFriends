package dev.no.name.afriends.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/** Недописанные сообщения по чатам. Остаются после закрытия окна и игры, как черновики в Telegram. */
final class Drafts {
    static final String JSON_KEY = "$drafts";
    static final int MAX_DRAFT = 1000;

    private final Map<String, String> drafts = new HashMap<>();

    String get(String key) {
        return drafts.getOrDefault(key, "");
    }

    /** Пустой текст убирает черновик, длинный обрезается. true — что-то поменялось. */
    boolean set(String key, String text) {
        String clean = text == null ? "" : text.length() > MAX_DRAFT ? text.substring(0, MAX_DRAFT) : text;
        String previous = clean.isBlank() ? drafts.remove(key) : drafts.put(key, clean);
        return !clean.isBlank() || previous != null;
    }

    void remove(String key) {
        drafts.remove(key);
    }

    void clear() {
        drafts.clear();
    }

    /** label — под каким именем чат пишется в файл (ник в исходном регистре). */
    void writeTo(JsonObject json, UnaryOperator<String> label) {
        JsonObject draftsJson = new JsonObject();
        drafts.forEach((key, text) -> draftsJson.addProperty(label.apply(key), text));
        json.add(JSON_KEY, draftsJson);
    }

    void read(JsonElement json) {
        if (!json.isJsonObject()) {
            return;
        }
        json.getAsJsonObject().entrySet().forEach(draft -> {
            if (draft.getValue().isJsonPrimitive()) {
                set(ChatId.storageKey(draft.getKey()), draft.getValue().getAsString());
            }
        });
    }
}
