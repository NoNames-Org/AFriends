package dev.no.name.blooddesigner.gui.friends;

import dev.no.name.blooddesigner.compat.Gfx;
import dev.no.name.blooddesigner.core.PrivateMessage;
import dev.no.name.blooddesigner.core.Reactions;
import dev.no.name.blooddesigner.gui.Theme;
import dev.no.name.blooddesigner.gui.render.Draw;
import dev.no.name.blooddesigner.gui.render.text.TextStyle;

/**
 * Всплывашка по правому клику на сообщение: ряд реакций, как в Telegram, а у картинки — ещё «Сохранить» и «Открыть».
 * Своя уже поставленная реакция подсвечена; клик по ней снимает
 */
final class ReactionBar {
    private static final int EMOJI = 12;
    private static final int CELL = 17;
    private static final int PAD = 4;
    private static final int BUTTON = 14;

    enum Kind { REACT, SAVE, OPEN }

    record Choice(Kind kind, String reaction) {
    }

    private PrivateMessage message;
    private boolean reactions;
    private boolean image;
    private float x;
    private float y;
    private float width;
    private float height;

    boolean isOpen() {
        return message != null;
    }

    PrivateMessage message() {
        return message;
    }

    void open(PrivateMessage message, boolean reactions, float mouseX, float mouseY, int areaWidth, int areaHeight) {
        this.message = message;
        this.reactions = reactions;
        this.image = message.image() != null;
        width = reactions ? Reactions.ALL.size() * CELL + PAD * 2 : 120;
        height = (reactions ? CELL + PAD * 2 : PAD) + (image ? BUTTON + PAD : 0);
        x = Math.max(4, Math.min(mouseX - width / 2, areaWidth - width - 4));
        // над курсором, а если не влезает — под ним
        y = mouseY - height - 6 >= 4 ? mouseY - height - 6 : Math.min(mouseY + 6, areaHeight - height - 4);
    }

    void close() {
        message = null;
    }

    void draw(Gfx g, int mouseX, int mouseY) {
        if (message == null) {
            return;
        }
        Draw.shadow(g, x, y + 2, width, height, Theme.RADIUS + 2, 10, Theme.SHADOW);
        Draw.box(g, x, y, width, height, Theme.RADIUS + 2, Theme.POPUP, Theme.BORDER);
        if (reactions) {
            for (int i = 0; i < Reactions.ALL.size(); i++) {
                String reaction = Reactions.ALL.get(i);
                float cellX = x + PAD + i * CELL;
                float cellY = y + PAD;
                boolean hover = inside(mouseX, mouseY, cellX, cellY, CELL, CELL);
                if (reaction.equals(message.reaction())) {
                    Draw.roundedRect(g, cellX, cellY, CELL, CELL, Theme.RADIUS, Theme.TILE_ON);
                } else if (hover) {
                    Draw.roundedRect(g, cellX, cellY, CELL, CELL, Theme.RADIUS, Theme.CARD_HOVER);
                }
                float size = hover ? EMOJI + 2 : EMOJI;
                ReactionChips.emoji(g, reaction, cellX + (CELL - size) / 2, cellY + (CELL - size) / 2, Math.round(size));
            }
        }
        if (image) {
            float rowY = y + height - PAD - BUTTON;
            float half = (width - PAD * 3) / 2;
            drawButton(g, "Сохранить", x + PAD, rowY, half, mouseX, mouseY);
            drawButton(g, "Открыть", x + PAD * 2 + half, rowY, half, mouseX, mouseY);
        }
    }

    // null — мимо всплывашки (её тогда закрывают), иначе что выбрали
    Choice click(double mouseX, double mouseY) {
        if (message == null || !inside(mouseX, mouseY, x, y, width, height)) {
            return null;
        }
        if (reactions && mouseY < y + PAD + CELL) {
            int index = (int) ((mouseX - x - PAD) / CELL);
            if (index >= 0 && index < Reactions.ALL.size()) {
                String reaction = Reactions.ALL.get(index);
                // своя такая же — значит снять
                return new Choice(Kind.REACT, reaction.equals(message.reaction()) ? null : reaction);
            }
            return new Choice(null, null);
        }
        if (image && mouseY >= y + height - PAD - BUTTON) {
            return new Choice(mouseX < x + width / 2 ? Kind.SAVE : Kind.OPEN, null);
        }
        return new Choice(null, null);
    }

    private static void drawButton(Gfx g, String label, float bx, float by, float bw, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, bx, by, bw, BUTTON);
        Draw.roundedRect(g, bx, by, bw, BUTTON, Theme.RADIUS, hover ? Theme.TILE_ON : Theme.TILE);
        Draw.text(g, label, bx + (bw - Draw.width(label, TextStyle.SMALL)) / 2, by + 4, Theme.TEXT, TextStyle.SMALL);
    }

    private static boolean inside(double px, double py, float bx, float by, float bw, float bh) {
        return px >= bx && px < bx + bw && py >= by && py < by + bh;
    }
}
