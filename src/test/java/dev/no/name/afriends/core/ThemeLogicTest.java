package dev.no.name.afriends.core;

import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.theme.ThemePreset;
import dev.no.name.afriends.gui.theme.ThemeSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ThemeLogicTest {
    @Test
    @DisplayName("тема по умолчанию — исходная монохромная палитра")
    void defaultIsGraphite() {
        ThemeSettings settings = new ThemeSettings();
        settings.select(ThemePreset.GRAPHITE.id());
        assertEquals(0xF2121318, Theme.PANEL_TOP);
        assertEquals(0xFFFFFFFF, Theme.ACCENT);
        assertEquals(0xFF0E0F12, Theme.ACCENT_TEXT);
    }

    @Test
    @DisplayName("другая тема перекрашивает акцент и фон")
    void presetRecolors() {
        ThemeSettings settings = new ThemeSettings();
        settings.select(ThemePreset.BLOOD.id());
        assertEquals(ThemePreset.BLOOD.accent(), Theme.ACCENT);
        assertNotEquals(0xF2121318, Theme.PANEL_TOP);
        settings.select(ThemePreset.GRAPHITE.id());
    }

    @Test
    @DisplayName("своя тема: текст на акценте выбирается по яркости и всё сохраняется")
    void customThemeRoundTrip() {
        ThemeSettings settings = new ThemeSettings();
        settings.setCustom(0x101820, 0xFFFFE066);
        settings.select(ThemeSettings.CUSTOM_ID);
        assertEquals(0xFFFFE066, Theme.ACCENT);

        assertEquals(0xFF0E0F12, Theme.ACCENT_TEXT);

        ThemeSettings loaded = new ThemeSettings();
        loaded.fromJson(settings.toJson());
        assertEquals(ThemeSettings.CUSTOM_ID, loaded.selectedId());
        assertEquals(0xFFFFE066, loaded.customAccent());
        assertEquals(0x101820, loaded.customBackground());
        settings.select(ThemePreset.GRAPHITE.id());
    }
}
