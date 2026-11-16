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

    enum Kind { REACT, SAVE, OPEN, FAVORITE }

    record Choice(Kind kind, String reaction) {
    }

    private PrivateMessage message;
    private dev.no.name.blooddesigner.core.MessageImage target;
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

    // картинка, по которой кликнули правой кнопкой (у сообщения с портала их может быть несколько)
    dev.no.name.blooddesigner.core.MessageImage target() {
        return target;
    }

    void open(PrivateMessage message, dev.no.name.blooddesigner.core.MessageImage target, boolean reactions,
              float mouseX, float mouseY, int areaWidth, int areaHeight) {
        this.message = message;
        this.target = target;
        this.reactions = reactions;
        this.image = target != null;
        // ширина — под ряд реакций и под кнопки картинки (у гифки их три)
        float imageWidth = target == null ? 0 : target.animated() ? 200 : 120;
        width = Math.max(reactions ? Reactions.ALL.size() * CELL + PAD * 2 : 0, imageWidth);
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
            String[] labels = buttons();
            float each = (width - PAD * (labels.length + 1)) / labels.length;
            for (int i = 0; i < labels.length; i++) {
                drawButton(g, labels[i], x + PAD + i * (each + PAD), rowY, each, mouseX, mouseY);
            }
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
            int count = buttons().length;
            int index = Math.max(0, Math.min(count - 1, (int) ((mouseX - x) / (width / count))));
            return new Choice(index == 0 ? Kind.SAVE : index == 1 ? Kind.OPEN : Kind.FAVORITE, null);
        }
        return new Choice(null, null);
    }

    // у гифки ещё звёздочка: в избранное или обратно
    private String[] buttons() {
        if (target != null && target.animated()) {
            boolean favorite = dev.no.name.blooddesigner.gui.media.GifFavorites.get().contains(target.sha256());
            return new String[] {"Сохранить", "Открыть", favorite ? "★ Убрать" : "★ В избранное"};
        }
        return new String[] {"Сохранить", "Открыть"};
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
