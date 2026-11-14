package dev.no.name.blooddesigner.gui.hub;

import dev.no.name.blooddesigner.compat.Gfx;
import dev.no.name.blooddesigner.compat.WidgetBase;
import dev.no.name.blooddesigner.core.MessageHistory;
import dev.no.name.blooddesigner.gui.Theme;
import dev.no.name.blooddesigner.gui.render.Colors;
import dev.no.name.blooddesigner.gui.render.Draw;
import dev.no.name.blooddesigner.gui.render.icon.Icon;
import dev.no.name.blooddesigner.gui.render.text.TextStyle;
import dev.no.name.blooddesigner.gui.sound.UiSound;
import dev.no.name.blooddesigner.gui.sound.UiSounds;
import dev.no.name.blooddesigner.gui.theme.ThemePreset;
import dev.no.name.blooddesigner.gui.theme.ThemeSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

public class SettingsPanel extends WidgetBase {
    private static final int PADDING = 8;
    private static final int HEADER_HEIGHT = 36;
    private static final int TILE_HEIGHT = 22;
    private static final int TILE_GAP = 5;
    private static final int COLUMNS = 2;
    private static final int SWATCH = 8;
    private static final int SLIDER_HEIGHT = 7;
    private static final int KNOB_WIDTH = 4;

    private static final float BACKGROUND_SATURATION = 0.45f;
    private static final float BACKGROUND_MIN = 0.03f;
    private static final float BACKGROUND_RANGE = 0.25f;
    private static final float ACCENT_SATURATION = 0.75f;
    private static final float ACCENT_MIN = 0.45f;
    private static final float ACCENT_RANGE = 0.55f;
    private static final int[] HUE_STOPS = {
            0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000
    };

    private enum Slider { BG_HUE, BG_VALUE, ACCENT_HUE, ACCENT_VALUE, HISTORY }

    private final ThemeSettings theme;
    private final PanelState state;

    private final Runnable onHistoryChange;
    private final int panelWidth;

    private float bgHue;
    private float bgValue;
    private float accentHue;
    private float accentValue;
    private Slider dragging;

    public SettingsPanel(int x, int y, int width, int height, ThemeSettings theme, PanelState state,
                         Runnable onHistoryChange) {
        super(x, y, width, height, Component.literal("Настройки"));
        this.panelWidth = width;
        this.theme = theme;
        this.state = state;
        this.onHistoryChange = onHistoryChange;
        float[] bg = Colors.toHsv(0xFF000000 | theme.customBackground());
        bgHue = bg[0];
        bgValue = clamp((bg[2] - BACKGROUND_MIN) / BACKGROUND_RANGE);
        float[] accent = Colors.toHsv(theme.customAccent());
        accentHue = accent[0];
        accentValue = clamp((accent[2] - ACCENT_MIN) / ACCENT_RANGE);
    }

    @Override
    protected void draw(Gfx g, int mouseX, int mouseY, float partialTick) {
        float open = state.progress();
        if (open <= 0.01f) {
            return;
        }
        Draw.setPixelScale(Minecraft.getInstance().getWindow().getGuiScale());
        int mx = state.isOpen() && open > 0.99f ? mouseX : -1000;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate((1 - open) * (panelWidth + 8), 0);
        Draw.pushAlpha(open);
        Draw.shadow(g, getX(), getY() + 2, panelWidth, height, Theme.PANEL_RADIUS, 14, Colors.fade(Theme.SHADOW, 0.7f));
        Draw.gradient(g, getX(), getY(), panelWidth, height, Theme.PANEL_RADIUS,
                Colors.fade(Theme.PANEL_TOP, 0.92f), Colors.fade(Theme.PANEL_BOTTOM, 0.92f));
        Draw.border(g, getX(), getY(), panelWidth, height, Theme.PANEL_RADIUS, Theme.BORDER);
        drawHeader(g);
        drawPresets(g, mx, mouseY);
        drawCustom(g);
        drawHistory(g);
        Draw.popAlpha();
        pose.popMatrix();
    }

    private void drawHeader(Gfx g) {
        int x = getX() + PADDING;
        Draw.roundedRect(g, x, getY() + 8, 20, 20, Theme.RADIUS, Theme.CARD_SELECTED);
        Draw.icon(g, Icon.SETTINGS, x + 5, getY() + 13, 10, Theme.ACCENT);
        Draw.text(g, "Настройки", x + 27, getY() + 10, Theme.TEXT, TextStyle.BODY_MEDIUM);
        Draw.text(g, "Оформление панели и меню", x + 27, getY() + 22, Theme.TEXT_DIM, TextStyle.SMALL);
        Draw.rect(g, x, getY() + HEADER_HEIGHT, panelWidth - PADDING * 2, 1, Theme.BORDER);
    }

    private int tileWidth() {
        return (panelWidth - PADDING * 2 - TILE_GAP) / COLUMNS;
    }

    private int presetsTop() {
        return getY() + HEADER_HEIGHT + 20;
    }

    private int tileX(int index) {
        return getX() + PADDING + index % COLUMNS * (tileWidth() + TILE_GAP);
    }

    private int tileY(int index) {
        return presetsTop() + index / COLUMNS * (TILE_HEIGHT + TILE_GAP);
    }

    private int tileCount() {
        return ThemePreset.values().length + 1;
    }

    private void drawPresets(Gfx g, int mouseX, int mouseY) {
        Draw.text(g, "Тема", getX() + PADDING, presetsTop() - 11, Theme.TEXT_DIM, TextStyle.SMALL);
        ThemePreset[] presets = ThemePreset.values();
        for (int i = 0; i < tileCount(); i++) {
            boolean custom = i == presets.length;
            String id = custom ? ThemeSettings.CUSTOM_ID : presets[i].id();
            String title = custom ? "Своя" : presets[i].title();
            int background = custom ? theme.customBackground() : presets[i].background();
            int accent = custom ? theme.customAccent() : presets[i].accent();
            drawTile(g, tileX(i), tileY(i), title, background, accent, id.equals(theme.selectedId()), mouseX, mouseY);
        }
    }

    private void drawTile(Gfx g, int x, int y, String title, int background, int accent, boolean selected,
                          int mouseX, int mouseY) {
        int width = tileWidth();
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + TILE_HEIGHT;
        int fill = selected ? Theme.CARD_SELECTED : hovered ? Theme.CARD_HOVER : Theme.CARD;
        int border = selected ? Theme.ACCENT : hovered ? Theme.BORDER_HOVER : Theme.BORDER;
        Draw.box(g, x, y, width, TILE_HEIGHT, Theme.RADIUS, fill, border);
        float swatchY = y + (TILE_HEIGHT - SWATCH) / 2f;
        Draw.box(g, x + 6, swatchY, SWATCH, SWATCH, SWATCH / 2f, 0xFF000000 | background, Theme.BORDER_HOVER);
        Draw.roundedRect(g, x + 6 + SWATCH - 2, swatchY, SWATCH, SWATCH, SWATCH / 2f, accent);
        Draw.text(g, Draw.trim(title, width - 30), x + 8 + SWATCH * 2, y + (TILE_HEIGHT - 8) / 2f,
                selected ? Theme.TEXT : Theme.TEXT_SOFT);
    }

    private int customTop() {
        return tileY(tileCount() - 1) + TILE_HEIGHT + 16;
    }

    private int sliderX() {
        return getX() + PADDING;
    }

    private int sliderWidth() {
        return panelWidth - PADDING * 2;
    }

    private int sliderY(Slider slider) {
        int top = customTop() + 12;
        return switch (slider) {
            case BG_HUE -> top + 12;
            case BG_VALUE -> top + 24;
            case ACCENT_HUE -> top + 50;
            case ACCENT_VALUE -> top + 62;
            case HISTORY -> top + 112;
        };
    }

    private void drawCustom(Gfx g) {
        int x = sliderX();
        Draw.text(g, "Своя тема", x, customTop() - 2, Theme.TEXT_DIM, TextStyle.SMALL);
        drawColorRow(g, "Фон", 0xFF000000 | theme.customBackground(), customTop() + 12);
        drawHue(g, sliderY(Slider.BG_HUE), bgHue);
        drawValue(g, sliderY(Slider.BG_VALUE), background(bgHue, 0), background(bgHue, 1), bgValue);
        drawColorRow(g, "Акцент", theme.customAccent(), customTop() + 50);
        drawHue(g, sliderY(Slider.ACCENT_HUE), accentHue);
        drawValue(g, sliderY(Slider.ACCENT_VALUE), accent(accentHue, 0), accent(accentHue, 1), accentValue);
        String hint = "Двигай ползунки — тема «Своя» включится сама";
        Draw.text(g, Draw.trim(hint, sliderWidth()), x, sliderY(Slider.ACCENT_VALUE) + 14, Theme.TEXT_FAINT, TextStyle.SMALL);
    }

    private static float historyPosition() {
        return (MessageHistory.limit() - MessageHistory.MIN) / (float) (MessageHistory.MAX - MessageHistory.MIN);
    }

    private void drawHistory(Gfx g) {
        int x = sliderX();
        int sliderY = sliderY(Slider.HISTORY);
        Draw.text(g, "Сообщения", x, sliderY - 26, Theme.TEXT_DIM, TextStyle.SMALL);
        Draw.text(g, "Хранить в каждом ЛС", x, sliderY - 13, Theme.TEXT_SOFT);
        String value = String.valueOf(MessageHistory.limit());
        Draw.text(g, value, x + sliderWidth() - Draw.width(value, TextStyle.BODY_MEDIUM), sliderY - 13, Theme.TEXT,
                TextStyle.BODY_MEDIUM);
        Draw.gradientHorizontal(g, x, sliderY, sliderWidth(), SLIDER_HEIGHT, 2, Theme.CARD_PRESSED, Theme.ACCENT);
        Draw.border(g, x, sliderY, sliderWidth(), SLIDER_HEIGHT, 2, Theme.BORDER_HOVER);
        drawKnob(g, x + historyPosition() * sliderWidth(), sliderY);
        String range = MessageHistory.MIN + " — " + MessageHistory.MAX + ", по умолчанию " + MessageHistory.DEFAULT;
        Draw.text(g, Draw.trim(range, sliderWidth()), x, sliderY + 12, Theme.TEXT_FAINT, TextStyle.SMALL);
    }

    private void drawColorRow(Gfx g, String label, int color, int y) {
        int x = sliderX();
        Draw.text(g, label, x, y - 1, Theme.TEXT_SOFT);
        Draw.box(g, x + sliderWidth() - 16, y - 2, 16, 9, Theme.RADIUS - 1, color, Theme.BORDER_HOVER);
    }

    private void drawHue(Gfx g, int y, float value) {
        int x = sliderX();
        float segment = sliderWidth() / (float) (HUE_STOPS.length - 1);
        for (int i = 0; i < HUE_STOPS.length - 1; i++) {
            Draw.gradientHorizontal(g, x + i * segment, y, segment + 0.5f, SLIDER_HEIGHT, 0, HUE_STOPS[i], HUE_STOPS[i + 1]);
        }
        Draw.border(g, x, y, sliderWidth(), SLIDER_HEIGHT, 2, Theme.BORDER_HOVER);
        drawKnob(g, x + value * sliderWidth(), y);
    }

    private void drawValue(Gfx g, int y, int from, int to, float value) {
        int x = sliderX();
        Draw.gradientHorizontal(g, x, y, sliderWidth(), SLIDER_HEIGHT, 2, from, to);
        Draw.border(g, x, y, sliderWidth(), SLIDER_HEIGHT, 2, Theme.BORDER_HOVER);
        drawKnob(g, x + value * sliderWidth(), y);
    }

    private static void drawKnob(Gfx g, float centerX, int y) {
        Draw.box(g, centerX - KNOB_WIDTH / 2f, y - 2, KNOB_WIDTH, SLIDER_HEIGHT + 4, 1.5f, 0xFFFFFFFF, 0xFF0E0F12);
    }

    private static int background(float hue, float value) {
        return Colors.fromHsv(hue, BACKGROUND_SATURATION, BACKGROUND_MIN + value * BACKGROUND_RANGE);
    }

    private static int accent(float hue, float value) {
        return Colors.fromHsv(hue, ACCENT_SATURATION, ACCENT_MIN + value * ACCENT_RANGE);
    }

    private static float clamp(float value) {
        return Math.max(0, Math.min(1, value));
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return state.isOpen() && state.progress() > 0.99f && super.isMouseOver(mouseX, mouseY);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        for (int i = 0; i < tileCount(); i++) {
            if (mouseX >= tileX(i) && mouseX < tileX(i) + tileWidth() && mouseY >= tileY(i) && mouseY < tileY(i) + TILE_HEIGHT) {
                ThemePreset[] presets = ThemePreset.values();
                theme.select(i == presets.length ? ThemeSettings.CUSTOM_ID : presets[i].id());
                UiSounds.play(UiSound.CLICK);
                return;
            }
        }
        for (Slider slider : Slider.values()) {
            int y = sliderY(slider);
            if (mouseY >= y - 3 && mouseY < y + SLIDER_HEIGHT + 3 && mouseX >= sliderX() - 2 && mouseX <= sliderX() + sliderWidth() + 2) {
                dragging = slider;
                updateSlider(mouseX);
                return;
            }
        }
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging != null) {
            updateSlider(event.x());
        }
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        dragging = null;
    }

    private void updateSlider(double mouseX) {
        float value = clamp((float) ((mouseX - sliderX()) / sliderWidth()));
        if (dragging == Slider.HISTORY) {
            int steps = Math.round(value * (MessageHistory.MAX - MessageHistory.MIN) / MessageHistory.STEP);
            int limit = MessageHistory.MIN + steps * MessageHistory.STEP;
            if (limit != MessageHistory.limit()) {
                MessageHistory.set(limit);
                onHistoryChange.run();
            }
            return;
        }
        switch (dragging) {
            case BG_HUE -> bgHue = value;
            case BG_VALUE -> bgValue = value;
            case ACCENT_HUE -> accentHue = value;
            case ACCENT_VALUE -> accentValue = value;
            default -> {
            }
        }
        theme.setCustom(background(bgHue, bgValue) & 0xFFFFFF, accent(accentHue, accentValue));
        if (!ThemeSettings.CUSTOM_ID.equals(theme.selectedId())) {
            theme.select(ThemeSettings.CUSTOM_ID);
        }
    }

    @Override
    public boolean shouldTakeFocusAfterInteraction() {
        return false;
    }
}
