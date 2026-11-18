package dev.no.name.afriends.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.no.name.afriends.core.voice.MicrophoneSelection;

import java.util.function.Consumer;

/**
 * Категория «Звук» в настройках AFriends: звуки интерфейса и выбранный микрофон для голосовых. Хранится
 * в config.json; звук сообщений — в настройках мессенджера. Громкость игры берётся из настроек Minecraft
 */
public final class SoundSettings {
    private static final String UI = "ui";
    private static final String MIC = "mic";

    private boolean uiSounds = true;
    // имя устройства OpenAL; пусто — микрофон по умолчанию
    private String microphone = "";
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

    /** Имя выбранного микрофона или пусто — по умолчанию. */
    public String microphone() {
        return microphone;
    }

    public void setMicrophone(String device) {
        microphone = device == null ? "" : device;
        MicrophoneSelection.select(microphone);
        onChange.run();
    }

    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty(UI, uiSounds);
        json.addProperty(MIC, microphone);
        return json;
    }

    public void fromJson(JsonElement json) {
        uiSounds = true;
        microphone = "";
        if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            if (object.has(UI) && object.get(UI).isJsonPrimitive()) {
                uiSounds = object.get(UI).getAsBoolean();
            }
            if (object.has(MIC) && object.get(MIC).isJsonPrimitive()) {
                microphone = object.get(MIC).getAsString();
            }
        }
        MicrophoneSelection.select(microphone);
        apply.accept(uiSounds);
    }
}
