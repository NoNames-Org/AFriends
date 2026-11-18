package dev.no.name.afriends.gui.settings;

import java.util.List;
import java.util.ArrayList;
import dev.no.name.afriends.api.SettingsCategory;
import dev.no.name.afriends.addon.Addons;
import dev.no.name.afriends.core.SoundSettings;
import com.mojang.blaze3d.platform.Window;
import dev.no.name.afriends.compat.Compat;
import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.compat.ScreenBase;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.animation.Animated;
import dev.no.name.afriends.gui.animation.Motion;
import dev.no.name.afriends.gui.render.Colors;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.blur.MenuBlur;
import dev.no.name.afriends.gui.render.icon.Icon;
import dev.no.name.afriends.gui.render.text.TextStyle;
import dev.no.name.afriends.gui.sound.UiSound;
import dev.no.name.afriends.gui.sound.UiSounds;
import dev.no.name.afriends.gui.theme.ThemePreset;
import dev.no.name.afriends.gui.theme.ThemeSettings;
import dev.no.name.afriends.gui.widget.ButtonWidget;
import dev.no.name.afriends.gui.widget.Widget;
import dev.no.name.afriends.net.game.LinkSettings;
import dev.no.name.afriends.net.service.LinkService;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

/** Полноэкранные настройки в стиле навигатора режимов: разделы слева, содержимое справа. */
public class SettingsScreen extends ScreenBase {
    private static final int WIDTH = 460;
    private static final int BODY_HEIGHT = 262;
    private static final float SCREEN_SHARE = 0.5f;
    private static final int MIN_TOP = 14;
    private static final int BAR_HEIGHT = 30;
    private static final int CONTROL_HEIGHT = 18;
    private static final int BAR_PAD = 6;
    private static final int BODY_GAP = 8;
    private static final int SIDEBAR_WIDTH = 112;
    private static final int SECTION_HEIGHT = 22;
    private static final int PAD = 12;

    private static final int TILE_HEIGHT = 24;
    private static final int TILE_GAP = 6;
    private static final int TILE_COLUMNS = 3;
    private static final int SWATCH = 9;
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

    private enum Kind { THEME, NETWORK, CATEGORY }

    /** Пункт слева: встроенный раздел или категория (своя или аддона). */
    private record Section(String title, String subtitle, Icon icon, Kind kind, SettingsCategory category) {
        static Section builtIn(String title, String subtitle, Icon icon, Kind kind) {
            return new Section(title, subtitle, icon, kind, null);
        }

        static Section of(SettingsCategory category) {
            Addons addons = Addons.get();
            return new Section(addons.guard("settingsCategory", category::title, ""),
                    addons.guard("settingsCategory", category::subtitle, ""),
                    addons.guard("settingsCategory", category::icon, Icon.SETTINGS), Kind.CATEGORY, category);
        }
    }

    private enum Slider { BG_HUE, BG_VALUE, ACCENT_HUE, ACCENT_VALUE }

    private final Screen parent;
    private final ThemeSettings theme;
    private final NetworkSection network;
    private final List<Section> sections;
    private final ButtonWidget close = new ButtonWidget(Icon.CLOSE, this::onClose);
    private final Animated windowOpen = new Animated(0, Motion.SLOW);

    private Section section;
    private Slider dragging;
    private float bgHue;
    private float bgValue;
    private float accentHue;
    private float accentValue;

    private float scale = 1;
    private int pixelScale = 1;
    private int left;
    private int top;

    public SettingsScreen(Screen parent, ThemeSettings theme, LinkSettings linkSettings, SoundSettings sound, LinkService link) {
        super(Component.literal("Настройки"));
        this.parent = parent;
        this.theme = theme;
        this.network = new NetworkSection(linkSettings, link);
        this.sections = sections(sound);
        this.section = sections.get(0);
        float[] bg = Colors.toHsv(0xFF000000 | theme.customBackground());
        bgHue = bg[0];
        bgValue = clamp((bg[2] - BACKGROUND_MIN) / BACKGROUND_RANGE);
        float[] accent = Colors.toHsv(theme.customAccent());
        accentHue = accent[0];
        accentValue = clamp((accent[2] - ACCENT_MIN) / ACCENT_RANGE);
        windowOpen.setTarget(1);
        UiSounds.play(UiSound.MENU_OPEN);
    }

    @Override
    protected void init() {
        Window window = minecraft.getWindow();
        int byWidth = Math.round(window.getWidth() * SCREEN_SHARE / WIDTH);
        int byHeight = window.getHeight() / (BAR_HEIGHT + BODY_GAP + BODY_HEIGHT + MIN_TOP * 2);
        pixelScale = Math.max(1, Math.min(byWidth, byHeight));
        scale = pixelScale / (float) window.getGuiScale();
        int canvasWidth = window.getWidth() / pixelScale;
        int canvasHeight = window.getHeight() / pixelScale;
        left = (canvasWidth - WIDTH) / 2;
        top = Math.max(MIN_TOP, (canvasHeight - BAR_HEIGHT - BODY_GAP - BODY_HEIGHT) / 2);
        close.setBounds(left + WIDTH - BAR_PAD - CONTROL_HEIGHT, top + (BAR_HEIGHT - CONTROL_HEIGHT) / 2,
                CONTROL_HEIGHT, CONTROL_HEIGHT);
    }

    // встроенные разделы, «Звук», затем категории аддонов
    private static List<Section> sections(SoundSettings sound) {
        List<Section> result = new ArrayList<>(List.of(
                Section.builtIn("Оформление", "Тема панели и меню", Icon.BRUSH, Kind.THEME),
                Section.builtIn("Сеть", "Друзья на любом сервере и защищённые ЛС", Icon.GLOBE, Kind.NETWORK),
                Section.of(SoundCategory.create(sound))));
        Addons.get().settingsCategories().forEach(category -> result.add(Section.of(category)));
        return List.copyOf(result);
    }

    // ---------- раскладка ----------

    private int bodyTop() {
        return top + BAR_HEIGHT + BODY_GAP;
    }

    private int contentX() {
        return left + SIDEBAR_WIDTH + BODY_GAP;
    }

    private int contentWidth() {
        return WIDTH - SIDEBAR_WIDTH - BODY_GAP;
    }

    private int innerX() {
        return contentX() + PAD;
    }

    private int innerWidth() {
        return contentWidth() - PAD * 2;
    }

    private int sectionY(int index) {
        return bodyTop() + 8 + index * (SECTION_HEIGHT + 4);
    }

    private int tilesTop() {
        return bodyTop() + 26;
    }

    private int tileWidth() {
        return (innerWidth() - TILE_GAP * (TILE_COLUMNS - 1)) / TILE_COLUMNS;
    }

    private int tileCount() {
        return ThemePreset.values().length + 1;
    }

    private int tileX(int index) {
        return innerX() + index % TILE_COLUMNS * (tileWidth() + TILE_GAP);
    }

    private int tileY(int index) {
        return tilesTop() + index / TILE_COLUMNS * (TILE_HEIGHT + TILE_GAP);
    }

    private int customTop() {
        return tileY(tileCount() - 1) + TILE_HEIGHT + 18;
    }

    private int sliderY(Slider slider) {
        int base = customTop() + 14;
        return switch (slider) {
            case BG_HUE -> base + 12;
            case BG_VALUE -> base + 25;
            case ACCENT_HUE -> base + 56;
            case ACCENT_VALUE -> base + 69;
        };
    }

    // ---------- отрисовка ----------

    @Override
    protected void drawBackground(Gfx g, int mouseX, int mouseY, float partialTick) {
        float shown = windowOpen.get();
        MenuBlur.request(shown);
        g.blurBeforeThisStratum();
        Draw.pushAlpha(shown);
        Draw.backdrop(g, width, height, Theme.DIM, Theme.DIM_VIGNETTE, Theme.DIM_GRAIN);
        Draw.popAlpha();
    }

    @Override
    protected void draw(Gfx g, int mouseX, int mouseY, float partialTick) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.scale(scale, scale);
        Draw.setPixelScale(pixelScale);
        int x = (int) toCanvas(mouseX);
        int y = (int) toCanvas(mouseY);
        float shown = windowOpen.get();
        Draw.pushAlpha(shown);
        Draw.pushOffset(g, 0, (1 - shown) * Motion.RISE);
        drawBar(g, x, y);
        drawSidebar(g, x, y);
        drawContentPanel(g);
        switch (section.kind()) {
            case THEME -> drawTheme(g, x, y);
            case NETWORK -> network.draw(g, innerX(), bodyTop() + 26, innerWidth(), x, y);
            case CATEGORY -> Addons.get().run("settingsCategory",
                    () -> section.category().draw(g, innerX(), bodyTop() + 28, innerWidth(), x, y));
        }
        Draw.popOffset(g);
        Draw.popAlpha();
        pose.popMatrix();
    }

    private void drawPanel(Gfx g, int x, int y, int w, int h) {
        Draw.shadow(g, x, y + 2, w, h, Theme.PANEL_RADIUS, 12, Colors.fade(Theme.SHADOW, 0.7f));
        Draw.gradient(g, x, y, w, h, Theme.PANEL_RADIUS, Theme.PANEL_TOP, Theme.PANEL_BOTTOM);
        Draw.border(g, x, y, w, h, Theme.PANEL_RADIUS, Theme.BORDER_HOVER);
    }

    private void drawBar(Gfx g, int mouseX, int mouseY) {
        drawPanel(g, left, top, WIDTH, BAR_HEIGHT);
        int iconBox = CONTROL_HEIGHT;
        int iconY = top + (BAR_HEIGHT - iconBox) / 2;
        Draw.roundedRect(g, left + BAR_PAD, iconY, iconBox, iconBox, Theme.RADIUS, Theme.CARD_SELECTED);
        Draw.icon(g, Icon.SETTINGS, left + BAR_PAD + (iconBox - 10) / 2f, iconY + (iconBox - 10) / 2f, 10, Theme.ACCENT);
        Draw.text(g, "Настройки", left + BAR_PAD + iconBox + 8, top + BAR_HEIGHT / 2f - 5, Theme.TEXT, TextStyle.TITLE);
        close.render(g, mouseX, mouseY);
    }

    private void drawSidebar(Gfx g, int mouseX, int mouseY) {
        drawPanel(g, left, bodyTop(), SIDEBAR_WIDTH, BODY_HEIGHT);
        for (int i = 0; i < sections.size(); i++) {
            Section item = sections.get(i);
            int x = left + 6;
            int y = sectionY(i);
            int w = SIDEBAR_WIDTH - 12;
            boolean active = item == section;
            boolean hovered = inside(mouseX, mouseY, x, y, w, SECTION_HEIGHT);
            int fill = active ? Theme.CARD_SELECTED : hovered ? Theme.CARD_HOVER : Theme.CARD;
            int border = active ? Theme.ACCENT : hovered ? Theme.BORDER_HOVER : Theme.BORDER;
            Draw.box(g, x, y, w, SECTION_HEIGHT, Theme.RADIUS, fill, border);
            int color = active ? Theme.ACCENT : hovered ? Theme.TEXT : Theme.TEXT_SOFT;
            Draw.icon(g, item.icon(), x + 7, y + (SECTION_HEIGHT - Theme.ICON_SMALL) / 2f, Theme.ICON_SMALL, color);
            Draw.text(g, item.title(), x + 12 + Theme.ICON_SMALL, y + (SECTION_HEIGHT - 8) / 2f,
                    active ? Theme.TEXT : Theme.TEXT_SOFT);
        }
    }

    private void drawContentPanel(Gfx g) {
        drawPanel(g, contentX(), bodyTop(), contentWidth(), BODY_HEIGHT);
        Draw.text(g, section.subtitle(), innerX(), bodyTop() + 10, Theme.TEXT_SOFT);
    }

    private void drawTheme(Gfx g, int mouseX, int mouseY) {
        ThemePreset[] presets = ThemePreset.values();
        for (int i = 0; i < tileCount(); i++) {
            boolean custom = i == presets.length;
            String id = custom ? ThemeSettings.CUSTOM_ID : presets[i].id();
            String title = custom ? "Своя" : presets[i].title();
            int background = custom ? theme.customBackground() : presets[i].background();
            int accent = custom ? theme.customAccent() : presets[i].accent();
            drawTile(g, tileX(i), tileY(i), title, background, accent, id.equals(theme.selectedId()), mouseX, mouseY);
        }
        int x = innerX();
        Draw.text(g, "Своя тема", x, customTop() - 4, Theme.TEXT_SOFT);
        drawColorRow(g, "Фон", 0xFF000000 | theme.customBackground(), sliderY(Slider.BG_HUE) - 12);
        drawHue(g, sliderY(Slider.BG_HUE), bgHue);
        drawValue(g, sliderY(Slider.BG_VALUE), background(bgHue, 0), background(bgHue, 1), bgValue);
        drawColorRow(g, "Акцент", theme.customAccent(), sliderY(Slider.ACCENT_HUE) - 12);
        drawHue(g, sliderY(Slider.ACCENT_HUE), accentHue);
        drawValue(g, sliderY(Slider.ACCENT_VALUE), accent(accentHue, 0), accent(accentHue, 1), accentValue);
        Draw.text(g, Draw.trim("Двигай ползунки — тема «Своя» включится сама", innerWidth()),
                x, sliderY(Slider.ACCENT_VALUE) + 16, Theme.TEXT_DIM);
    }

    private void drawTile(Gfx g, int x, int y, String title, int background, int accent, boolean selected,
                          int mouseX, int mouseY) {
        int w = tileWidth();
        boolean hovered = inside(mouseX, mouseY, x, y, w, TILE_HEIGHT);
        int fill = selected ? Theme.CARD_SELECTED : hovered ? Theme.CARD_HOVER : Theme.CARD;
        int border = selected ? Theme.ACCENT : hovered ? Theme.BORDER_HOVER : Theme.BORDER;
        Draw.box(g, x, y, w, TILE_HEIGHT, Theme.RADIUS, fill, border);
        float swatchY = y + (TILE_HEIGHT - SWATCH) / 2f;
        Draw.box(g, x + 7, swatchY, SWATCH, SWATCH, SWATCH / 2f, 0xFF000000 | background, Theme.BORDER_HOVER);
        Draw.roundedRect(g, x + 7 + SWATCH - 2, swatchY, SWATCH, SWATCH, SWATCH / 2f, accent);
        Draw.text(g, Draw.trim(title, w - 34), x + 10 + SWATCH * 2, y + (TILE_HEIGHT - 8) / 2f,
                selected ? Theme.TEXT : Theme.TEXT_SOFT);
    }

    private void drawColorRow(Gfx g, String label, int color, int y) {
        int x = innerX();
        Draw.text(g, label, x, y - 1, Theme.TEXT_SOFT);
        Draw.box(g, x + innerWidth() - 18, y - 2, 18, 9, Theme.RADIUS - 1, color, Theme.BORDER_HOVER);
    }

    private void drawHue(Gfx g, int y, float value) {
        int x = innerX();
        float segment = innerWidth() / (float) (HUE_STOPS.length - 1);
        for (int i = 0; i < HUE_STOPS.length - 1; i++) {
            Draw.gradientHorizontal(g, x + i * segment, y, segment + 0.5f, SLIDER_HEIGHT, 0, HUE_STOPS[i], HUE_STOPS[i + 1]);
        }
        Draw.border(g, x, y, innerWidth(), SLIDER_HEIGHT, 2, Theme.BORDER_HOVER);
        drawKnob(g, x + value * innerWidth(), y);
    }

    private void drawValue(Gfx g, int y, int from, int to, float value) {
        int x = innerX();
        Draw.gradientHorizontal(g, x, y, innerWidth(), SLIDER_HEIGHT, 2, from, to);
        Draw.border(g, x, y, innerWidth(), SLIDER_HEIGHT, 2, Theme.BORDER_HOVER);
        drawKnob(g, x + value * innerWidth(), y);
    }

    private static void drawKnob(Gfx g, float centerX, int y) {
        Draw.box(g, centerX - KNOB_WIDTH / 2f, y - 2, KNOB_WIDTH, SLIDER_HEIGHT + 4, 1.5f, 0xFFFFFFFF, 0xFF0E0F12);
    }

    // ---------- ввод ----------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = toCanvas(event.x());
        double y = toCanvas(event.y());
        int button = Widget.LEFT_BUTTON;
        if (close.mouseClicked(x, y, button)) {
            return true;
        }
        if (clickSection(x, y)) {
            return true;
        }
        if (section.kind() == Kind.NETWORK) {
            return network.click(x, y, innerX(), bodyTop() + 26, innerWidth());
        }
        if (section.kind() == Kind.CATEGORY) {
            return Addons.get().guard("settingsCategory",
                    () -> section.category().click(x, y, innerX(), bodyTop() + 28, innerWidth()), false);
        }
        return clickTile(x, y) || startDrag(x, y);
    }

    private boolean clickSection(double x, double y) {
        for (int i = 0; i < sections.size(); i++) {
            if (inside(x, y, left + 6, sectionY(i), SIDEBAR_WIDTH - 12, SECTION_HEIGHT)) {
                if (section != sections.get(i)) {
                    section = sections.get(i);
                    UiSounds.play(UiSound.CLICK);
                }
                return true;
            }
        }
        return false;
    }

    private boolean clickTile(double x, double y) {
        ThemePreset[] presets = ThemePreset.values();
        for (int i = 0; i < tileCount(); i++) {
            if (inside(x, y, tileX(i), tileY(i), tileWidth(), TILE_HEIGHT)) {
                theme.select(i == presets.length ? ThemeSettings.CUSTOM_ID : presets[i].id());
                UiSounds.play(UiSound.CLICK);
                return true;
            }
        }
        return false;
    }

    private boolean startDrag(double x, double y) {
        for (Slider slider : Slider.values()) {
            int sy = sliderY(slider);
            if (y >= sy - 3 && y < sy + SLIDER_HEIGHT + 3 && x >= innerX() - 2 && x <= innerX() + innerWidth() + 2) {
                dragging = slider;
                updateSlider(x);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging == null) {
            return false;
        }
        updateSlider(toCanvas(event.x()));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = null;
        return true;
    }

    private void updateSlider(double canvasX) {
        float value = clamp((float) ((canvasX - innerX()) / innerWidth()));
        switch (dragging) {
            case BG_HUE -> bgHue = value;
            case BG_VALUE -> bgValue = value;
            case ACCENT_HUE -> accentHue = value;
            case ACCENT_VALUE -> accentValue = value;
        }
        theme.setCustom(background(bgHue, bgValue) & 0xFFFFFF, accent(accentHue, accentValue));
        if (!ThemeSettings.CUSTOM_ID.equals(theme.selectedId())) {
            theme.select(ThemeSettings.CUSTOM_ID);
        }
    }

    @Override
    public void onClose() {
        Compat.setScreen(parent);
    }

    @Override
    public void removed() {
        MenuBlur.cancel();
        Compat.stopTextInput();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------- утилиты ----------

    private double toCanvas(double screenValue) {
        return screenValue / scale;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
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
}
