package dev.no.name.blooddesigner.gui.friends;

import dev.no.name.blooddesigner.compat.Gfx;
import dev.no.name.blooddesigner.gui.Theme;
import dev.no.name.blooddesigner.gui.media.ImageTextures;
import dev.no.name.blooddesigner.gui.render.Draw;
import dev.no.name.blooddesigner.gui.render.icon.Icon;
import dev.no.name.blooddesigner.gui.render.text.TextStyle;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Прикреплённая, но ещё не отправленная картинка — над полем ввода, как в Telegram: превью, имя, размер и крестик.
 * Отправляется вместе с текстом из поля: картинка сверху, текст подписью
 */
final class AttachmentStrip {
    static final int HEIGHT = 26;
    private static final int THUMB = 22;

    private Path file;
    private long size;
    private float closeX;
    private float closeY;

    boolean isStaged() {
        return file != null;
    }

    Path file() {
        return file;
    }

    void stage(Path file) {
        this.file = file;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            size = 0;
        }
    }

    void clear() {
        file = null;
    }

    void draw(Gfx g, float x, float y, float width, int mouseX, int mouseY) {
        if (file == null) {
            return;
        }
        Draw.box(g, x, y, width, HEIGHT, Theme.RADIUS, Theme.CARD, Theme.BORDER);
        boolean animated = file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gif");
        ImageTextures.Entry entry = ImageTextures.get().file(file, animated);
        Identifier texture = entry.state() == ImageTextures.State.READY ? entry.texture(System.currentTimeMillis()) : null;
        float thumbX = x + 2;
        float thumbY = y + 2;
        if (texture != null) {
            // в квадрат по меньшей стороне, без искажения
            float scale = THUMB / (float) Math.max(entry.width(), entry.height());
            int w = Math.max(1, Math.round(entry.width() * scale));
            int h = Math.max(1, Math.round(entry.height() * scale));
            Draw.roundedRect(g, thumbX, thumbY, THUMB, THUMB, Theme.RADIUS - 1, Theme.TILE);
            g.image(texture, Math.round(thumbX + (THUMB - w) / 2f), Math.round(thumbY + (THUMB - h) / 2f), w, h, entry.width(), entry.height());
        } else {
            Draw.roundedRect(g, thumbX, thumbY, THUMB, THUMB, Theme.RADIUS - 1, Theme.TILE);
            Draw.icon(g, Icon.IMAGE, thumbX + (THUMB - Theme.ICON_SMALL) / 2f, thumbY + (THUMB - Theme.ICON_SMALL) / 2f,
                    Theme.ICON_SMALL, Theme.TEXT_DIM);
        }
        float textX = thumbX + THUMB + 6;
        int textWidth = (int) (x + width - 20 - textX);
        Draw.text(g, Draw.trim(file.getFileName().toString(), textWidth), textX, y + 4, Theme.TEXT);
        String info = (animated ? "Гифка" : "Изображение") + " · " + sizeText(size) + " · подпись — в поле ниже";
        Draw.text(g, Draw.trim(info, textWidth, TextStyle.SMALL), textX, y + 15, Theme.TEXT_DIM, TextStyle.SMALL);
        closeX = x + width - 16;
        closeY = y + (HEIGHT - 12) / 2f;
        boolean hover = mouseX >= closeX && mouseX < closeX + 12 && mouseY >= closeY && mouseY < closeY + 12;
        Draw.icon(g, Icon.CLOSE, closeX + 2, closeY + 2, Theme.ICON_SMALL, hover ? Theme.TEXT : Theme.TEXT_DIM);
    }

    // true — нажали крестик, картинка убрана
    boolean click(double mouseX, double mouseY) {
        if (file != null && mouseX >= closeX && mouseX < closeX + 12 && mouseY >= closeY && mouseY < closeY + 12) {
            clear();
            return true;
        }
        return false;
    }

    private static String sizeText(long bytes) {
        return bytes >= 1024 * 1024 ? String.format(Locale.ROOT, "%.1f МБ", bytes / 1048576f) : Math.max(1, bytes / 1024) + " КБ";
    }
}
