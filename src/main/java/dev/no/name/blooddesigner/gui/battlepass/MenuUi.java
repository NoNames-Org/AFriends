package dev.no.name.blooddesigner.gui.battlepass;

import dev.no.name.blooddesigner.compat.Gfx;
import dev.no.name.blooddesigner.gui.Theme;
import dev.no.name.blooddesigner.gui.render.Colors;
import dev.no.name.blooddesigner.gui.render.Draw;
import dev.no.name.blooddesigner.gui.render.icon.Icon;
import dev.no.name.blooddesigner.gui.render.text.TextStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/** Общие элементы экранов БП, квестов и магазина: баннер, панели, кнопки, полосы, перенос текста. */
final class MenuUi {
    static final int GOLD = 0xFFF2C14E;
    static final int GREEN = 0xFF5BD47A;
    static final int RED = 0xFFFF6B6B;
    static final int BLOOD = 0xFFE51B1B;
    static final int BUTTON_HEIGHT = 20;

    enum ButtonStyle { PRIMARY, LIGHT, OUTLINE }

    private MenuUi() {
    }

    static void panel(Gfx g, float x, float y, float w, float h) {
        Draw.shadow(g, x, y + 3, w, h, Theme.PANEL_RADIUS, 14, Colors.fade(Theme.SHADOW, 0.75f));
        Draw.gradient(g, x, y, w, h, Theme.PANEL_RADIUS, Theme.PANEL_TOP, Theme.PANEL_BOTTOM);
        Draw.border(g, x, y, w, h, Theme.PANEL_RADIUS, Theme.BORDER_HOVER);
    }

    /** Яркий баннер в цвет темы: градиент, косые полосы и мягкое свечение, как обложка сезона. */
    static void banner(Gfx g, float x, float y, float w, float h, Icon emblem) {
        int deep = Colors.mix(Theme.ACCENT, 0xFF05060A, 0.78f);
        int mid = Colors.mix(Theme.ACCENT, 0xFF05060A, 0.55f);
        Draw.shadow(g, x, y + 3, w, h, Theme.PANEL_RADIUS, 16, Colors.fade(Theme.SHADOW, 0.8f));
        Draw.gradientHorizontal(g, x, y, w, h, Theme.PANEL_RADIUS, deep, mid);
        g.enableScissor((int) x, (int) y, (int) (x + w), (int) (y + h));
        Matrix3x2fStack pose = g.pose();
        for (int i = 0; i < 6; i++) {
            pose.pushMatrix();
            pose.translate(x + w * 0.35f + i * 34, y - 20);
            pose.rotate((float) Math.toRadians(24));
            Draw.rect(g, 0, 0, 14, h * 2, Colors.fade(Theme.ACCENT, 0.07f - i * 0.008f));
            pose.popMatrix();
        }
        float glow = h * 1.4f;
        Draw.roundedRect(g, x + w - glow * 0.75f, y + h / 2f - glow / 2f, glow, glow, glow / 2f, Colors.fade(Theme.ACCENT, 0.16f));
        if (emblem != null) {
            float size = h * 0.72f;
            Draw.icon(g, emblem, x + w - size - h * 0.22f, y + (h - size) / 2f, size, Colors.fade(Theme.ACCENT, 0.35f));
        }
        Draw.gradient(g, x, y + h * 0.55f, w, h * 0.45f, 0, 0x00000000, 0x55000000);
        g.disableScissor();
        Draw.border(g, x, y, w, h, Theme.PANEL_RADIUS, Colors.fade(Theme.ACCENT, 0.45f));
    }

    /**
     * Крупный заголовок. Шрифт мода растрирован под размер пикселя, поэтому вместо растяжения
     * берём шрифт под увеличенный размер: так буквы остаются чёткими, а не пиксельными.
     */
    static void headline(Gfx g, String text, float x, float y, float scale, int color) {
        int base = Draw.pixelScale();
        int sharp = sharpScale(base, scale);
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(sharp / (float) base, sharp / (float) base);
        Draw.setPixelScale(sharp);
        Draw.text(g, text, 0, 0, color, TextStyle.TITLE);
        Draw.setPixelScale(base);
        pose.popMatrix();
    }

    static float headlineWidth(String text, float scale) {
        int base = Draw.pixelScale();
        return Draw.width(text, TextStyle.TITLE) * sharpScale(base, scale) / (float) base;
    }

    /** Ближайший размер пикселя, под который есть шрифт (1..8). */
    private static int sharpScale(int base, float scale) {
        return Math.max(1, Math.min(TextStyle.MAX_PIXEL_SCALE, Math.round(base * scale)));
    }

    static void button(Gfx g, float x, float y, float w, Icon icon, String label, ButtonStyle style,
                       boolean enabled, boolean hovered) {
        int fill;
        int border;
        int color;
        switch (style) {
            case PRIMARY -> {
                fill = hovered ? Colors.mix(Theme.ACCENT, 0xFFFFFFFF, 0.18f) : Theme.ACCENT;
                border = fill;
                color = Theme.ACCENT_TEXT;
            }
            case LIGHT -> {
                fill = hovered ? 0xFFFFFFFF : 0xFFE9EAEE;
                border = fill;
                color = 0xFF111216;
            }
            default -> {
                fill = hovered ? Theme.CARD_HOVER : Colors.fade(Theme.CARD, 0.85f);
                border = hovered ? Theme.BORDER_SELECTED : Theme.BORDER_HOVER;
                color = hovered ? Theme.TEXT : Theme.TEXT_SOFT;
            }
        }
        if (!enabled) {
            fill = Colors.fade(Theme.CARD, 0.7f);
            border = Theme.BORDER;
            color = Theme.TEXT_FAINT;
        }
        Draw.box(g, x, y, w, BUTTON_HEIGHT, Theme.RADIUS, fill, border);
        float centerY = y + BUTTON_HEIGHT / 2f;
        if (label == null || label.isEmpty()) {
            Draw.icon(g, icon, x + (w - Theme.ICON_SMALL) / 2f, centerY - Theme.ICON_SMALL / 2f, Theme.ICON_SMALL, color);
            return;
        }
        float iconSpace = icon == null ? 0 : Theme.ICON_SMALL + 5;
        float contentX = x + (w - iconSpace - Draw.width(label)) / 2f;
        if (icon != null) {
            Draw.icon(g, icon, contentX, centerY - Theme.ICON_SMALL / 2f, Theme.ICON_SMALL, color);
        }
        Draw.text(g, label, contentX + iconSpace, centerY - 4, color);
    }

    /** Плашка баланса бладиков, правым краем в {@code rightX}. Ничего не рисует, если баланс неизвестен. */
    static void balance(Gfx g, float rightX, float y, int balance) {
        if (balance < 0) {
            return;
        }
        String text = String.valueOf(balance);
        float w = Theme.ICON_SMALL + 5 + Draw.width(text, TextStyle.BODY_MEDIUM) + 14;
        float x = rightX - w;
        Draw.box(g, x, y, w, BUTTON_HEIGHT, BUTTON_HEIGHT / 2f, 0x66000000, Colors.fade(BLOOD, 0.6f));
        Draw.icon(g, Icon.GEM, x + 7, y + (BUTTON_HEIGHT - Theme.ICON_SMALL) / 2f, Theme.ICON_SMALL, BLOOD);
        Draw.text(g, text, x + 7 + Theme.ICON_SMALL + 5, y + (BUTTON_HEIGHT - 8) / 2f, Theme.TEXT, TextStyle.BODY_MEDIUM);
    }

    static void progressBar(Gfx g, float x, float y, float w, float h, float fill, int color) {
        Draw.roundedRect(g, x, y, w, h, h / 2f, Colors.fade(0xFF000000, 0.45f));
        if (fill > 0) {
            Draw.roundedRect(g, x, y, Math.max(h, w * Math.min(1, fill)), h, h / 2f, color);
        }
    }

    /** Предмет, увеличенный в {@code scale} раз, левым верхним углом в (x, y). */
    static void item(Gfx g, Font font, ItemStack stack, float x, float y, float scale) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        g.item(font, stack, 0, 0);
        pose.popMatrix();
    }

    /** Перенос по словам в ширину {@code maxWidth}; не больше {@code maxLines} строк, последняя с «…». */
    static List<String> wrap(String text, int maxWidth, int maxLines, TextStyle style) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (Draw.width(candidate, style) <= maxWidth || line.isEmpty()) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            lines.add(line.toString());
            line.setLength(0);
            line.append(word);
            if (lines.size() == maxLines) {
                break;
            }
        }
        if (!line.isEmpty() && lines.size() < maxLines) {
            lines.add(line.toString());
        } else if (!line.isEmpty() && !lines.isEmpty()) {
            int last = lines.size() - 1;
            lines.set(last, Draw.trim(lines.get(last) + " " + line, maxWidth, style));
        }
        return lines;
    }

    /** Текст, повёрнутый на 90° против часовой стрелки, по центру точки (centerX, centerY). */
    static void verticalText(Gfx g, String text, float centerX, float centerY, int color, TextStyle style) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(centerX, centerY);
        pose.rotate((float) (-Math.PI / 2));
        Draw.text(g, text, -Draw.width(text, style) / 2f, -4, color, style);
        pose.popMatrix();
    }
}
