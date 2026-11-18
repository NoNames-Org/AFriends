package dev.no.name.afriends.gui.settings;

import dev.no.name.afriends.api.SettingsCategory;
import dev.no.name.afriends.core.SoundSettings;
import dev.no.name.afriends.gui.render.icon.Icon;

import java.util.List;

/** Категория «Звук» в настройках AFriends. Звук сообщений — в настройках мессенджера. */
final class SoundCategory {
    private SoundCategory() {
    }

    static SettingsCategory create(SoundSettings sound) {
        return SettingsCategory.toggles("Звук", "Звуки AFriends", Icon.BELL, List.of(
                new SettingsCategory.Toggle("Звуки интерфейса", "Щелчки кнопок, открытие окон и меню",
                        sound::uiSounds, sound::toggleUiSounds)),
                "Звук новых сообщений — в настройках мессенджера (шестерёнка внизу слева).");
    }
}
