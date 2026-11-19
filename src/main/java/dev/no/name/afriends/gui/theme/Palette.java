package dev.no.name.afriends.gui.theme;

/**
 * Цвета оформления одним неизменяемым набором. Тема меняется целиком заменой набора,
 * поэтому окно никогда не увидит половину старых и половину новых цветов
 */
public record Palette(int dim, int panelTop, int panelBottom, int shadow, int popup,
                      int border, int borderHover, int borderSelected,
                      int card, int cardHover, int cardSelected, int cardPressed,
                      int text, int textSoft, int textDim, int textFaint,
                      int tile, int tileOn, int hudBackground, int hudBorder,
                      int accent, int accentText) {

    /** Тёмная тема по умолчанию. */
    public static final Palette GRAPHITE = new Palette(0x8C060710, 0xF2121318, 0xF20B0C0F, 0x99000000, 0xFF15161A,
            0xFF26282E, 0xFF3A3D45, 0xFF6F737C,
            0xFF16171B, 0xFF1B1D22, 0xFF202227, 0xFF2A2C33,
            0xFFFFFFFF, 0xFFA9ADB6, 0xFF8B8F98, 0xFF5E626B,
            0xFF1F2126, 0xFF2C2E35, 0xE00D0E12, 0xFF23252B,
            0xFFFFFFFF, 0xFF0E0F12);
}
