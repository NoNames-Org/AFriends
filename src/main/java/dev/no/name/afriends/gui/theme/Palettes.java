package dev.no.name.afriends.gui.theme;

/** Как из цвета фона и акцента получается весь набор цветов. Чистые функции: ничего не меняют, только считают. */
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

    static Palette graphite() {
        return Palette.GRAPHITE;
    }

    /** Своя тема: оттенки панелей из фона, акцент как есть; текст и тень — как у графита. */
    static Palette custom(int background, int accent) {
        Palette graphite = Palette.GRAPHITE;
        int base = 0xFF000000 | background;
        int border = mix(base, WHITE, 0.12f);
        int tile = mix(base, WHITE, 0.09f);
        return new Palette(
                0x8C000000 | mix(base, 0xFF000000, 0.4f) & 0xFFFFFF,
                PANEL_ALPHA | mix(base, WHITE, 0.03f) & 0xFFFFFF,
                PANEL_ALPHA | base & 0xFFFFFF,
                graphite.shadow(),
                mix(base, WHITE, 0.05f),
                border,
                mix(base, WHITE, 0.2f),
                mix(border, accent, BORDER_TINT),
                mix(base, WHITE, 0.05f),
                mix(base, WHITE, 0.08f),
                mix(base, WHITE, 0.11f),
                mix(base, WHITE, 0.15f),
                graphite.text(),
                graphite.textSoft(),
                graphite.textDim(),
                graphite.textFaint(),
                tile,
                mix(tile, accent, TILE_TINT),
                HUD_ALPHA | base & 0xFFFFFF,
                mix(base, WHITE, 0.1f),
                0xFF000000 | accent,
                luminance(accent) > LIGHT_ACCENT ? DARK_TEXT : WHITE);
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
