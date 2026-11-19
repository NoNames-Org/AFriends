package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.gui.media.ChatMedia;
import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.core.MessageImage;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.media.ImageTextures;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.text.TextStyle;
import net.minecraft.resources.Identifier;

/** Картинка во всё окно переписки по клику: крупно, с кнопками «Сохранить» и «Открыть». Клик мимо или Esc — закрыть */
final class ImageViewer {
    private static final int MARGIN = 16;
    private static final int BUTTON_WIDTH = 70;
    private static final int BUTTON_HEIGHT = 16;

    enum Action { NONE, SAVE, OPEN, CLOSE }

    private MessageImage image;
    private float buttonsY;
    private float buttonsX;

    boolean isOpen() {
        return image != null;
    }

    MessageImage image() {
        return image;
    }

    void open(MessageImage image) {
        this.image = image;
    }

    void close() {
        image = null;
    }

    void draw(Gfx g, ChatMedia media, int width, int height, int mouseX, int mouseY) {
        if (image == null) {
            return;
        }
        Draw.roundedRect(g, 0, 0, width, height, Theme.PANEL_RADIUS, 0xE6050608);
        // пока крупная версия собирается, показываем маленькую из чата, чтобы не мигала «Загрузка…»
        ImageTextures.Entry entry = media.fullImage(image);
        if (entry.state() != ImageTextures.State.READY) {
            ImageTextures.Entry preview = media.image(image);
            if (preview.state() == ImageTextures.State.READY || entry.state() == ImageTextures.State.FAILED) {
                entry = preview;
            }
        }
        float areaWidth = width - MARGIN * 2;
        float areaHeight = height - MARGIN * 3 - BUTTON_HEIGHT;
        float scale = Math.min(areaWidth / image.width(), areaHeight / image.height());
        scale = Math.min(scale, Math.max(1, areaWidth / 120f));
        float w = Math.round(image.width() * scale);
        float h = Math.round(image.height() * scale);
        float x = (width - w) / 2;
        float y = MARGIN + (areaHeight - h) / 2;
        Identifier texture = entry.state() == ImageTextures.State.READY ? entry.texture(System.currentTimeMillis()) : null;
        if (texture != null) {
            g.image(texture, Math.round(x), Math.round(y), Math.round(w), Math.round(h), entry.width(), entry.height());
        } else {
            String label = entry.state() == ImageTextures.State.FAILED ? "Картинка не загрузилась" : "Загрузка…";
            Draw.textCentered(g, label, width / 2f, height / 2f - 4, Theme.TEXT_SOFT);
        }
        buttonsY = height - MARGIN - BUTTON_HEIGHT;
        buttonsX = (width - (BUTTON_WIDTH * 3 + 8)) / 2f;
        drawButton(g, "Сохранить", buttonsX, mouseX, mouseY);
        drawButton(g, "Открыть", buttonsX + BUTTON_WIDTH + 4, mouseX, mouseY);
        drawButton(g, "Закрыть", buttonsX + (BUTTON_WIDTH + 4) * 2, mouseX, mouseY);
    }

    Action click(double mouseX, double mouseY) {
        if (image == null) {
            return Action.NONE;
        }
        if (mouseY >= buttonsY && mouseY < buttonsY + BUTTON_HEIGHT) {
            int index = (int) Math.floor((mouseX - buttonsX) / (BUTTON_WIDTH + 4));
            if (index == 0) {
                return Action.SAVE;
            }
            if (index == 1) {
                return Action.OPEN;
            }
        }
        return Action.CLOSE;
    }

    private void drawButton(Gfx g, String label, float x, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + BUTTON_WIDTH && mouseY >= buttonsY && mouseY < buttonsY + BUTTON_HEIGHT;
        Draw.box(g, x, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT, Theme.RADIUS, hover ? Theme.TILE_ON : Theme.TILE, Theme.BORDER);
        Draw.text(g, label, x + (BUTTON_WIDTH - Draw.width(label, TextStyle.SMALL)) / 2, buttonsY + 5, Theme.TEXT, TextStyle.SMALL);
    }
}
