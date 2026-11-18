package dev.no.name.afriends.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.function.Consumer;

/** Категория «Звук» в настройках AFriends. Хранится в config.json; звук сообщений — в настройках мессенджера. */
public final class SoundSettings {
    private static final String UI = "ui";

    private boolean uiSounds = true;
    private Runnable onChange = () -> { };
    private Consumer<Boolean> apply = value -> { };

    public void setOnChange(Runnable listener) {
        onChange = listener;
    }

    /** Куда передать значение, когда оно меняется или загружается (звуки интерфейса). */
    public void setApply(Consumer<Boolean> target) {
        apply = target;
        apply.accept(uiSounds);
    }

    public boolean uiSounds() {
        return uiSounds;
    }

    public void toggleUiSounds() {
        uiSounds = !uiSounds;
        apply.accept(uiSounds);
        onChange.run();
    }

    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty(UI, uiSounds);
        return json;
    }

    public void fromJson(JsonElement json) {
        uiSounds = true;
        if (json.isJsonObject() && json.getAsJsonObject().has(UI) && json.getAsJsonObject().get(UI).isJsonPrimitive()) {
            uiSounds = json.getAsJsonObject().get(UI).getAsBoolean();
        }
        apply.accept(uiSounds);
    }
}
