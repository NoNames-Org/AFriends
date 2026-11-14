package dev.no.name.blooddesigner.gui.theme;

import dev.no.name.blooddesigner.gui.Theme;

final class Palettes {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int PANEL_ALPHA = 0xF4000000;
    private static final int HUD_ALPHA = 0xE0000000;
    private static final int DARK_TEXT = 0xFF0E0F12;

    private static final float LIGHT_ACCENT = 0.6f;
    private static final float BORDER_TINT = 0.6f;
    private static final float TILE_TINT = 0.16f;

    private Palettes() {
    }

    static void applyGraphite() {
        Theme.DIM = 0x8C060710;
        Theme.PANEL_TOP = 0xF2121318;
        Theme.PANEL_BOTTOM = 0xF20B0C0F;
        Theme.SHADOW = 0x99000000;
        Theme.POPUP = 0xFF15161A;
        Theme.BORDER = 0xFF26282E;
        Theme.BORDER_HOVER = 0xFF3A3D45;
        Theme.BORDER_SELECTED = 0xFF6F737C;
        Theme.CARD = 0xFF16171B;
        Theme.CARD_HOVER = 0xFF1B1D22;
        Theme.CARD_SELECTED = 0xFF202227;
        Theme.CARD_PRESSED = 0xFF2A2C33;
        Theme.TEXT = 0xFFFFFFFF;
        Theme.TEXT_SOFT = 0xFFA9ADB6;
        Theme.TEXT_DIM = 0xFF8B8F98;
        Theme.TEXT_FAINT = 0xFF5E626B;
        Theme.TILE = 0xFF1F2126;
        Theme.TILE_ON = 0xFF2C2E35;
        Theme.HUD_BACKGROUND = 0xE00D0E12;
        Theme.HUD_BORDER = 0xFF23252B;
        Theme.ACCENT = 0xFFFFFFFF;
        Theme.ACCENT_TEXT = DARK_TEXT;
    }

    static void apply(int background, int accent) {
        applyGraphite();
        int base = 0xFF000000 | background;
        Theme.DIM = 0x8C000000 | mix(base, 0xFF000000, 0.4f) & 0xFFFFFF;
        Theme.PANEL_TOP = PANEL_ALPHA | mix(base, WHITE, 0.03f) & 0xFFFFFF;
        Theme.PANEL_BOTTOM = PANEL_ALPHA | base & 0xFFFFFF;
        Theme.POPUP = mix(base, WHITE, 0.05f);
        Theme.CARD = mix(base, WHITE, 0.05f);
        Theme.CARD_HOVER = mix(base, WHITE, 0.08f);
        Theme.CARD_SELECTED = mix(base, WHITE, 0.11f);
        Theme.CARD_PRESSED = mix(base, WHITE, 0.15f);
        Theme.BORDER = mix(base, WHITE, 0.12f);
        Theme.BORDER_HOVER = mix(base, WHITE, 0.2f);
        Theme.TILE = mix(base, WHITE, 0.09f);
        Theme.HUD_BACKGROUND = HUD_ALPHA | base & 0xFFFFFF;
        Theme.HUD_BORDER = mix(base, WHITE, 0.1f);
        Theme.ACCENT = 0xFF000000 | accent;
        Theme.ACCENT_TEXT = luminance(accent) > LIGHT_ACCENT ? DARK_TEXT : WHITE;
        Theme.TILE_ON = mix(Theme.TILE, accent, TILE_TINT);
        Theme.BORDER_SELECTED = mix(Theme.BORDER, accent, BORDER_TINT);
    }

    static float luminance(int color) {
        float r = (color >> 16 & 0xFF) / 255f;
        float g = (color >> 8 & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        return 0.299f * r + 0.587f * g + 0.114f * b;
    }

    private static int mix(int from, int to, float amount) {
        int r = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * amount);
        int g = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * amount);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * amount);
        return from & 0xFF000000 | r << 16 | g << 8 | b;
    }
}
