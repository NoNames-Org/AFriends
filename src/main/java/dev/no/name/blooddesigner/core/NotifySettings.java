package dev.no.name.blooddesigner.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Звук входящих сообщений: общий выключатель и чаты без звука, как «Выключить уведомления» в Telegram.
 * Чат — ник друга или служебный ключ ($blooddesigner, группа). Хранится в config.json, общий для всех серверов
 */
public final class NotifySettings {
    private static final String SOUND = "sound";
    private static final String MUTED = "muted";

    private boolean sound = true;
    private final Set<String> muted = new TreeSet<>();
    private Runnable onChange = () -> { };

    public void setOnChange(Runnable listener) {
        onChange = listener;
    }

    public boolean soundEnabled() {
        return sound;
    }

    public void toggleSound() {
        sound = !sound;
        onChange.run();
    }

    public boolean isMuted(String chat) {
        return chat != null && muted.contains(key(chat));
    }

    // true — теперь без звука
    public boolean toggleMuted(String chat) {
        boolean now = !muted.remove(key(chat)) && muted.add(key(chat));
        onChange.run();
        return now;
    }

    // звенеть ли на новое сообщение в этом чате
    public boolean shouldRing(String chat) {
        return sound && !isMuted(chat);
    }

    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty(SOUND, sound);
        JsonArray list = new JsonArray();
        muted.forEach(list::add);
        json.add(MUTED, list);
        return json;
    }

    public void fromJson(JsonElement json) {
        muted.clear();
        sound = true;
        if (!json.isJsonObject()) {
            return;
        }
        JsonObject object = json.getAsJsonObject();
        if (object.has(SOUND) && object.get(SOUND).isJsonPrimitive()) {
            sound = object.get(SOUND).getAsBoolean();
        }
        if (object.has(MUTED) && object.get(MUTED).isJsonArray()) {
            object.getAsJsonArray(MUTED).forEach(element -> muted.add(key(element.getAsString())));
        }
    }

    private static String key(String chat) {
        return chat.toLowerCase(Locale.ROOT);
    }
}
