package dev.no.name.afriends.gui;

import dev.no.name.afriends.gui.theme.Palette;

/**
 * Размеры оформления и текущие цвета. Цвета — один неизменяемый {@link Palette}: тему меняет только
 * {@code ThemeSettings} заменой набора целиком, остальной код цвета лишь читает
 */
public final class Theme {
    public static final int RADIUS = 4;
    public static final int PANEL_RADIUS = 8;
    public static final float DIM_VIGNETTE = 0.55f;
    public static final float DIM_GRAIN = 0.35f;
    public static final long IDLE_PERIOD = 3600;
    // опасные действия и черновики: одинаковый красный во всех окнах
    public static final int DANGER = 0xFFFF6B6B;
    public static final int ICON_SMALL = 8;
    public static final int ICON = 10;

    private static volatile Palette palette = Palette.GRAPHITE;

    private Theme() {
    }

    public static Palette palette() {
        return palette;
    }

    /** Сменить тему целиком; зовёт только ThemeSettings. */
    public static void use(Palette next) {
        palette = next;
    }

    public static int dim() {
        return palette.dim();
    }

    public static int panelTop() {
        return palette.panelTop();
    }

    public static int panelBottom() {
        return palette.panelBottom();
    }

    public static int shadow() {
        return palette.shadow();
    }

    public static int popup() {
        return palette.popup();
    }

    public static int border() {
        return palette.border();
    }

    public static int borderHover() {
        return palette.borderHover();
    }

    public static int borderSelected() {
        return palette.borderSelected();
    }

    public static int card() {
        return palette.card();
    }

    public static int cardHover() {
        return palette.cardHover();
    }

    public static int cardSelected() {
        return palette.cardSelected();
    }

    public static int cardPressed() {
        return palette.cardPressed();
    }

    public static int text() {
        return palette.text();
    }

    public static int textSoft() {
        return palette.textSoft();
    }

    public static int textDim() {
        return palette.textDim();
    }

    public static int textFaint() {
        return palette.textFaint();
    }

    public static int tile() {
        return palette.tile();
    }

    public static int tileOn() {
        return palette.tileOn();
    }

    public static int hudBackground() {
        return palette.hudBackground();
    }

    public static int hudBorder() {
        return palette.hudBorder();
    }

    public static int accent() {
        return palette.accent();
    }

    public static int accentText() {
        return palette.accentText();
    }
}
