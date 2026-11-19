package dev.no.name.afriends.gui.theme;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PalettesTest {
    @Test
    @DisplayName("графит — готовый набор по умолчанию")
    void graphiteIsDefault() {
        assertSame(Palette.GRAPHITE, Palettes.graphite());
        assertEquals(0xF2121318, Palette.GRAPHITE.panelTop());
        assertEquals(0xFF0E0F12, Palette.GRAPHITE.accentText());
    }

    @Test
    @DisplayName("своя тема: панели из фона, акцент как есть, текст и тень от графита")
    void customKeepsGraphiteText() {
        Palette custom = Palettes.custom(0x102030, 0xFFE066);

        assertEquals(0xFFFFE066, custom.accent());
        assertEquals(0xF4102030, custom.panelBottom());
        assertEquals(Palette.GRAPHITE.text(), custom.text());
        assertEquals(Palette.GRAPHITE.shadow(), custom.shadow());
        assertNotEquals(Palette.GRAPHITE.border(), custom.border());
    }

    @Test
    @DisplayName("на светлом акценте тёмный текст, на тёмном — белый")
    void accentTextContrast() {
        assertEquals(0xFF0E0F12, Palettes.custom(0x000000, 0xFFFFFF).accentText());
        assertEquals(0xFFFFFFFF, Palettes.custom(0x000000, 0x202020).accentText());
    }
}
