package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.gui.media.ChatMedia;
import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.core.MessageImage;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.media.ImageTextures;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.icon.Icon;
import dev.no.name.afriends.gui.render.text.TextStyle;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Общие кусочки окон профиля и групп: кнопки, вкладки, галочки, плашки-теги, голова игрока и аватарка группы.
 * Всё рисуется в координатах холста мессенджера
 */
final class PanelKit {
    static final int BUTTON = 16;
    static final int GROUP_COLOR = 0xFF3B6FD8;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm");

    private PanelKit() {
    }

    static boolean inside(double mouseX, double mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    // кнопка с подписью: accent — главная (залитая), danger — красная подпись, disabled — тусклая
    static void button(Gfx g, String label, float x, float y, float width, boolean accent, boolean danger, boolean disabled,
                       int mouseX, int mouseY) {
        boolean hover = !disabled && inside(mouseX, mouseY, x, y, width, BUTTON);
        int fill = accent && !disabled ? Theme.ACCENT : hover ? Theme.CARD_HOVER : Theme.CARD;
        Draw.box(g, x, y, width, BUTTON, Theme.RADIUS, fill, accent && !disabled ? Theme.ACCENT : Theme.BORDER);
        int color = disabled ? Theme.TEXT_FAINT : accent ? Theme.ACCENT_TEXT : danger ? Theme.DANGER : hover ? Theme.TEXT : Theme.TEXT_SOFT;
        String shown = Draw.trim(label, (int) width - 6, TextStyle.SMALL);
        Draw.text(g, shown, x + (width - Draw.width(shown, TextStyle.SMALL)) / 2f, y + 5, color, TextStyle.SMALL);
    }

    static float buttonWidth(String label) {
        return Draw.width(label, TextStyle.SMALL) + 14;
    }

    // ряд вкладок во всю ширину; active — индекс выбранной
    static void tabs(Gfx g, String[] labels, int active, float x, float y, float width, int mouseX, int mouseY) {
        float step = width / labels.length;
        for (int i = 0; i < labels.length; i++) {
            float tabX = x + i * step;
            boolean hover = inside(mouseX, mouseY, tabX, y, step, BUTTON);
            int color = i == active ? Theme.TEXT : hover ? Theme.TEXT_SOFT : Theme.TEXT_DIM;
            Draw.text(g, labels[i], tabX + (step - Draw.width(labels[i], TextStyle.SMALL)) / 2f, y + 4, color, TextStyle.SMALL);
            if (i == active) {
                Draw.roundedRect(g, tabX + 6, y + BUTTON - 2, step - 12, 2, 1, Theme.ACCENT);
            }
        }
        Draw.rect(g, x, y + BUTTON, width, 1, Theme.BORDER);
    }

    // какая вкладка под курсором; -1 — мимо
    static int tabAt(double mouseX, double mouseY, int count, float x, float y, float width) {
        if (!inside(mouseX, mouseY, x, y, width, BUTTON)) {
            return -1;
        }
        return Math.min(count - 1, (int) ((mouseX - x) / (width / count)));
    }

    static void checkbox(Gfx g, boolean on, boolean disabled, float x, float y) {
        int fill = on && !disabled ? Theme.ACCENT : Theme.CARD;
        Draw.box(g, x, y, 8, 8, 2, fill, on && !disabled ? Theme.ACCENT : disabled ? Theme.BORDER : Theme.BORDER_HOVER);
        if (on) {
            Draw.icon(g, Icon.CHECK, x + 1, y + 1, 6, disabled ? Theme.TEXT_FAINT : Theme.ACCENT_TEXT);
        }
    }

    // маленькая плашка с текстом (префикс, «владелец»); возвращает ширину
    static float tag(Gfx g, String text, float x, float y, int color) {
        float width = Draw.width(text, TextStyle.SMALL) + 6;
        Draw.roundedRect(g, x, y, width, 9, 4.5f, Theme.TILE_ON);
        Draw.text(g, text, x + 3, y + 1.5f, color, TextStyle.SMALL);
        return width;
    }

    static float tagWidth(String text) {
        return Draw.width(text, TextStyle.SMALL) + 6;
    }

    static void face(Gfx g, PlayerSkin skin, float x, float y, int size) {
        if (skin != null) {
            g.face(skin, Math.round(x), Math.round(y), size);
            return;
        }
        Draw.gradient(g, x, y, size, size, Theme.RADIUS - 1, Theme.TILE_ON, Theme.TILE);
        float icon = size * 0.5f;
        Draw.icon(g, Icon.USER, x + (size - icon) / 2, y + (size - icon) / 2, icon, Theme.TEXT_DIM);
    }

    // аватарка группы, а пока её нет или она грузится — синий кружок со значком
    static void groupTile(Gfx g, MessageImage avatar, ChatMedia media, float x, float y, int size) {
        if (avatar != null) {
            ImageTextures.Entry entry = media.image(avatar);
            if (texture(g, entry, x, y, size)) {
                return;
            }
        }
        Draw.roundedRect(g, x, y, size, size, size / 2f, GROUP_COLOR);
        float icon = size * 0.55f;
        Draw.icon(g, Icon.USERS, x + (size - icon) / 2, y + (size - icon) / 2, icon, 0xFFFFFFFF);
    }

    // картинка из файла (выбранное фото новой группы); false — ещё не загрузилась
    static boolean filePreview(Gfx g, ChatMedia media, Path file, float x, float y, int size) {
        return texture(g, media.file(file), x, y, size);
    }

    // текстура в квадрат по большей стороне, без искажения
    private static boolean texture(Gfx g, ImageTextures.Entry entry, float x, float y, int size) {
        Identifier texture = entry.state() == ImageTextures.State.READY ? entry.texture(System.currentTimeMillis()) : null;
        if (texture == null || entry.width() <= 0 || entry.height() <= 0) {
            return false;
        }
        float scale = size / (float) Math.max(entry.width(), entry.height());
        int w = Math.max(1, Math.round(entry.width() * scale));
        int h = Math.max(1, Math.round(entry.height() * scale));
        Draw.roundedRect(g, x, y, size, size, Theme.RADIUS, Theme.TILE);
        g.image(texture, Math.round(x + (size - w) / 2f), Math.round(y + (size - h) / 2f), w, h, entry.width(), entry.height());
        return true;
    }

    static String stamp(long time) {
        return STAMP.format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()));
    }

    // окно-карточка с тенью
    static void frame(Gfx g, float x, float y, float width, float height) {
        Draw.shadow(g, x, y + 2, width, height, Theme.RADIUS + 2, 12, Theme.SHADOW);
        Draw.box(g, x, y, width, height, Theme.RADIUS + 2, Theme.POPUP, Theme.BORDER);
    }
}
