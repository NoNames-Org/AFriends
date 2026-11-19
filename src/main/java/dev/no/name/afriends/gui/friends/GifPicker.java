package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.media.GifFavorites;
import dev.no.name.afriends.gui.media.ImageTextures;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.text.TextStyle;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Избранные гифки над полем ввода: сетка с живой анимацией, колёсико листает.
 * Клик — гифка сразу уходит собеседнику, правый клик — убрать из избранного
 */
final class GifPicker {
    private static final int COLUMNS = 4;
    private static final int CELL = 40;
    private static final int GAP = 3;
    private static final int PAD = 5;
    private static final int TITLE = 13;
    private static final int VISIBLE_ROWS = 3;

    sealed interface Click {
    }

    record Send(GifFavorites.Gif gif) implements Click {
    }

    record Remove(GifFavorites.Gif gif) implements Click {
    }

    record Inside() implements Click {
    }

    private final GifFavorites favorites;
    private final ImageTextures images;
    private boolean open;
    private int scrollRow;
    private float x;
    private float y;

    GifPicker(GifFavorites favorites, ImageTextures images) {
        this.favorites = favorites;
        this.images = images;
    }

    boolean isOpen() {
        return open;
    }

    // справа над полем ввода; right/bottom — где кончается полоса ввода
    void toggle(float right, float bottom) {
        open = !open;
        scrollRow = 0;
        x = right - width();
        y = bottom - height() - 4;
    }

    void close() {
        open = false;
    }

    static float width() {
        return PAD * 2 + COLUMNS * CELL + (COLUMNS - 1) * GAP;
    }

    static float height() {
        return PAD * 2 + TITLE + VISIBLE_ROWS * CELL + (VISIBLE_ROWS - 1) * GAP;
    }

    void draw(Gfx g, int mouseX, int mouseY) {
        if (!open) {
            return;
        }
        float w = width();
        float h = height();
        Draw.shadow(g, x, y + 2, w, h, Theme.RADIUS + 2, 10, Theme.SHADOW);
        Draw.box(g, x, y, w, h, Theme.RADIUS + 2, Theme.POPUP, Theme.BORDER);
        Draw.text(g, "Избранные гифки", x + PAD, y + PAD, Theme.TEXT_SOFT, TextStyle.SMALL);
        List<GifFavorites.Gif> gifs = favorites.all();
        if (gifs.isEmpty()) {
            float centerX = x + w / 2;
            Draw.textCentered(g, "Пока пусто", centerX, y + h / 2 - 8, Theme.TEXT_SOFT);
            String hint = "правый клик по гифке → ★";
            Draw.text(g, hint, centerX - Draw.width(hint, TextStyle.SMALL) / 2f, y + h / 2 + 4, Theme.TEXT_DIM, TextStyle.SMALL);
            return;
        }
        long now = System.currentTimeMillis();
        for (int i = scrollRow * COLUMNS; i < gifs.size() && i < (scrollRow + VISIBLE_ROWS) * COLUMNS; i++) {
            int slot = i - scrollRow * COLUMNS;
            float cellX = x + PAD + (slot % COLUMNS) * (CELL + GAP);
            float cellY = y + PAD + TITLE + (slot / COLUMNS) * (CELL + GAP);
            boolean hover = mouseX >= cellX && mouseX < cellX + CELL && mouseY >= cellY && mouseY < cellY + CELL;
            Draw.roundedRect(g, cellX, cellY, CELL, CELL, Theme.RADIUS, hover ? Theme.TILE_ON : Theme.TILE);
            ImageTextures.Entry entry = images.file(gifs.get(i).file(), true);
            Identifier texture = entry.state() == ImageTextures.State.READY ? entry.texture(now) : null;
            if (texture != null) {
                float scale = (CELL - 2) / (float) Math.max(entry.width(), entry.height());
                int tw = Math.max(1, Math.round(entry.width() * scale));
                int th = Math.max(1, Math.round(entry.height() * scale));
                g.image(texture, Math.round(cellX + (CELL - tw) / 2f), Math.round(cellY + (CELL - th) / 2f), tw, th, entry.width(), entry.height());
            }
        }
    }

    // null — мимо панели (её закрывают)
    Click click(double mouseX, double mouseY, boolean right) {
        if (!open || mouseX < x || mouseX >= x + width() || mouseY < y || mouseY >= y + height()) {
            return null;
        }
        List<GifFavorites.Gif> gifs = favorites.all();
        double localX = mouseX - x - PAD;
        double localY = mouseY - y - PAD - TITLE;
        if (localX < 0 || localY < 0) {
            return new Inside();
        }
        int column = (int) (localX / (CELL + GAP));
        int row = (int) (localY / (CELL + GAP));
        if (column >= COLUMNS || row >= VISIBLE_ROWS) {
            return new Inside();
        }
        int index = (scrollRow + row) * COLUMNS + column;
        if (index >= gifs.size()) {
            return new Inside();
        }
        return right ? new Remove(gifs.get(index)) : new Send(gifs.get(index));
    }

    // true — колёсико над панелью, прокрутили её
    boolean scroll(double mouseX, double mouseY, double amount) {
        if (!open || mouseX < x || mouseX >= x + width() || mouseY < y || mouseY >= y + height()) {
            return false;
        }
        int rows = (favorites.all().size() + COLUMNS - 1) / COLUMNS;
        scrollRow = Math.max(0, Math.min(Math.max(0, rows - VISIBLE_ROWS), scrollRow + (amount < 0 ? 1 : -1)));
        return true;
    }
}
