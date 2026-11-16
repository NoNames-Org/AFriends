package dev.no.name.blooddesigner.gui.friends;

import dev.no.name.blooddesigner.compat.Gfx;
import dev.no.name.blooddesigner.core.MessageImage;
import dev.no.name.blooddesigner.core.PrivateMessage;
import dev.no.name.blooddesigner.core.Reactions;
import dev.no.name.blooddesigner.gui.Theme;
import dev.no.name.blooddesigner.gui.media.GifFavorites;
import dev.no.name.blooddesigner.gui.render.Draw;
import dev.no.name.blooddesigner.gui.render.text.TextStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * Всплывашка по правому клику на сообщение: ряд реакций, как в Telegram (если на сообщение можно реагировать),
 * и ряд действий — «Переслать», а у картинки ещё «Сохранить», «Открыть» и у гифки «★».
 * Своя уже поставленная реакция подсвечена; клик по ней снимает
 */
final class ReactionBar {
    private static final int EMOJI = 12;
    private static final int CELL = 17;
    private static final int PAD = 4;
    private static final int BUTTON = 14;
    private static final int BUTTON_WIDTH = 64;

    enum Kind { REACT, FORWARD, SAVE, OPEN, FAVORITE }

    record Choice(Kind kind, String reaction) {
    }

    private record Action(Kind kind, String label) {
    }

    private PrivateMessage message;
    private MessageImage target;
    private boolean reactions;
    private List<Action> actions = List.of();
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
    MessageImage target() {
        return target;
    }

    void open(PrivateMessage message, MessageImage target, boolean reactions, float mouseX, float mouseY, int areaWidth, int areaHeight) {
        this.message = message;
        this.target = target;
        this.reactions = reactions;
        this.actions = actionsFor(target);
        float actionsWidth = actions.size() * BUTTON_WIDTH + (actions.size() + 1) * PAD;
        width = Math.max(reactions ? Reactions.ALL.size() * CELL + PAD * 2 : 0, actionsWidth);
        height = (reactions ? CELL + PAD : 0) + PAD + BUTTON + PAD;
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
            float rowX = x + (width - Reactions.ALL.size() * CELL) / 2;
            for (int i = 0; i < Reactions.ALL.size(); i++) {
                String reaction = Reactions.ALL.get(i);
                float cellX = rowX + i * CELL;
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
        float rowY = y + height - PAD - BUTTON;
        float each = (width - PAD * (actions.size() + 1)) / actions.size();
        for (int i = 0; i < actions.size(); i++) {
            drawButton(g, actions.get(i).label(), x + PAD + i * (each + PAD), rowY, each, mouseX, mouseY);
        }
    }

    // null — мимо всплывашки (её тогда закрывают), иначе что выбрали
    Choice click(double mouseX, double mouseY) {
        if (message == null || !inside(mouseX, mouseY, x, y, width, height)) {
            return null;
        }
        if (reactions && mouseY < y + PAD + CELL) {
            float rowX = x + (width - Reactions.ALL.size() * CELL) / 2;
            int index = (int) Math.floor((mouseX - rowX) / CELL);
            if (index >= 0 && index < Reactions.ALL.size()) {
                String reaction = Reactions.ALL.get(index);
                // своя такая же — значит снять
                return new Choice(Kind.REACT, reaction.equals(message.reaction()) ? null : reaction);
            }
            return new Choice(null, null);
        }
        if (mouseY >= y + height - PAD - BUTTON) {
            int index = Math.max(0, Math.min(actions.size() - 1, (int) ((mouseX - x) / (width / actions.size()))));
            return new Choice(actions.get(index).kind(), null);
        }
        return new Choice(null, null);
    }

    private static List<Action> actionsFor(MessageImage target) {
        List<Action> list = new ArrayList<>();
        list.add(new Action(Kind.FORWARD, "Переслать"));
        if (target != null) {
            list.add(new Action(Kind.SAVE, "Сохранить"));
            list.add(new Action(Kind.OPEN, "Открыть"));
            if (target.animated()) {
                list.add(new Action(Kind.FAVORITE, GifFavorites.get().contains(target.sha256()) ? "★ Убрать" : "★ В избранное"));
            }
        }
        return List.copyOf(list);
    }

    private static void drawButton(Gfx g, String label, float bx, float by, float bw, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, bx, by, bw, BUTTON);
        Draw.roundedRect(g, bx, by, bw, BUTTON, Theme.RADIUS, hover ? Theme.TILE_ON : Theme.TILE);
        String shown = Draw.trim(label, (int) bw - 4, TextStyle.SMALL);
        Draw.text(g, shown, bx + (bw - Draw.width(shown, TextStyle.SMALL)) / 2, by + 4, Theme.TEXT, TextStyle.SMALL);
    }

    private static boolean inside(double px, double py, float bx, float by, float bw, float bh) {
        return px >= bx && px < bx + bw && py >= by && py < by + bh;
    }
}
