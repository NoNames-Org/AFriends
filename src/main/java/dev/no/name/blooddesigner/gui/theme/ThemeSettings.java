package dev.no.name.blooddesigner.gui.theme;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class ThemeSettings {
    public static final String CUSTOM_ID = "custom";
    private static final int DEFAULT_CUSTOM_BACKGROUND = 0x101418;
    private static final int DEFAULT_CUSTOM_ACCENT = 0xFFFF5C7A;

    private String selectedId = ThemePreset.GRAPHITE.id();
    private int customBackground = DEFAULT_CUSTOM_BACKGROUND;
    private int customAccent = DEFAULT_CUSTOM_ACCENT;
    private Runnable onChange = () -> { };

    public void setOnChange(Runnable listener) {
        onChange = listener;
    }

    public String selectedId() {
        return selectedId;
    }

    public int customBackground() {
        return customBackground;
    }

    public int customAccent() {
        return customAccent;
    }

    public void select(String id) {
        selectedId = CUSTOM_ID.equals(id) || ThemePreset.find(id) != null ? id : ThemePreset.GRAPHITE.id();
        apply();
        onChange.run();
    }

    public void setCustom(int background, int accent) {
        customBackground = background & 0xFFFFFF;
        customAccent = 0xFF000000 | accent;
        if (CUSTOM_ID.equals(selectedId)) {
            apply();
        }
        onChange.run();
    }

    public void apply() {
        if (CUSTOM_ID.equals(selectedId)) {
            Palettes.apply(customBackground, customAccent);
            return;
        }
        ThemePreset preset = ThemePreset.find(selectedId);
        if (preset == null || preset == ThemePreset.GRAPHITE) {
            Palettes.applyGraphite();
        } else {
            Palettes.apply(preset.background(), preset.accent());
        }
    }

    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("selected", selectedId);
        json.addProperty("customBackground", customBackground);
        json.addProperty("customAccent", customAccent);
        return json;
    }

    public void fromJson(JsonElement json) {
        if (!json.isJsonObject()) {
            return;
        }
        JsonObject object = json.getAsJsonObject();
        if (object.has("customBackground")) {
            customBackground = object.get("customBackground").getAsInt() & 0xFFFFFF;
        }
        if (object.has("customAccent")) {
            customAccent = 0xFF000000 | object.get("customAccent").getAsInt();
        }
        String id = object.has("selected") ? object.get("selected").getAsString() : ThemePreset.GRAPHITE.id();
        selectedId = CUSTOM_ID.equals(id) || ThemePreset.find(id) != null ? id : ThemePreset.GRAPHITE.id();
        apply();
    }
}
