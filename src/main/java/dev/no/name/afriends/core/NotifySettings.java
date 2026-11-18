package dev.no.name.afriends.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Звук и всплывающие уведомления входящих сообщений: общие выключатели и чаты без звука, как «Выключить уведомления» в Telegram.
 * Чат — ник друга или служебный ключ ($blooddesigner, группа). Хранится в config.json, общий для всех серверов
 */
public final class NotifySettings {
    private static final String SOUND = "sound";
    private static final String MUTED = "muted";
    private static final String READ_RECEIPTS = "readReceipts";
    private static final String TOASTS = "toasts";

    private boolean sound = true;
    // всплывающие карточки о новых сообщениях в правом верхнем углу
    private boolean toasts = true;
    // отправлять ли собеседникам «прочитано»; «доставлено» уходит всегда
    private boolean readReceipts = true;
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

    public boolean toastsEnabled() {
        return toasts;
    }

    public void toggleToasts() {
        toasts = !toasts;
        onChange.run();
    }

    public boolean readReceipts() {
        return readReceipts;
    }

    public void toggleReadReceipts() {
        readReceipts = !readReceipts;
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

    // показывать ли всплывающее уведомление о новом сообщении в этом чате
    public boolean shouldToast(String chat) {
        return toasts && !isMuted(chat);
    }

    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty(SOUND, sound);
        json.addProperty(TOASTS, toasts);
        json.addProperty(READ_RECEIPTS, readReceipts);
        JsonArray list = new JsonArray();
        muted.forEach(list::add);
        json.add(MUTED, list);
        return json;
    }

    public void fromJson(JsonElement json) {
        muted.clear();
        sound = true;
        toasts = true;
        readReceipts = true;
        if (!json.isJsonObject()) {
            return;
        }
        JsonObject object = json.getAsJsonObject();
        if (object.has(SOUND) && object.get(SOUND).isJsonPrimitive()) {
            sound = object.get(SOUND).getAsBoolean();
        }
        if (object.has(TOASTS) && object.get(TOASTS).isJsonPrimitive()) {
            toasts = object.get(TOASTS).getAsBoolean();
        }
        if (object.has(READ_RECEIPTS) && object.get(READ_RECEIPTS).isJsonPrimitive()) {
            readReceipts = object.get(READ_RECEIPTS).getAsBoolean();
        }
        if (object.has(MUTED) && object.get(MUTED).isJsonArray()) {
            object.getAsJsonArray(MUTED).forEach(element -> muted.add(key(element.getAsString())));
        }
    }

    private static String key(String chat) {
        return chat.toLowerCase(Locale.ROOT);
    }
}
