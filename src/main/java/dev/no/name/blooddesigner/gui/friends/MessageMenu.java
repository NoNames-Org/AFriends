package dev.no.name.blooddesigner.gui.friends;

import dev.no.name.blooddesigner.compat.Gfx;
import dev.no.name.blooddesigner.core.PrivateMessage;
import dev.no.name.blooddesigner.core.Reactions;
import dev.no.name.blooddesigner.gui.Theme;
import dev.no.name.blooddesigner.gui.render.Draw;

import java.util.List;
import java.util.function.Consumer;

/**
 * Меню по правому клику на сообщение, как в Telegram Desktop: сверху отдельная плашка реакций (если на сообщение
 * можно реагировать), под ней список действий (его собирает MessageActions). Своя реакция подсвечена, клик по ней снимает
 */
final class MessageMenu {
    private static final int EMOJI = 12;
    private static final int CELL = 17;
    private static final int PAD = 4;
    private static final int GAP = 4;
    private static final int PILL_HEIGHT = CELL + PAD * 2;

    private final ChatMenu list = new ChatMenu();
    private PrivateMessage message;
    private Consumer<String> react;
    private float pillX;
    private float pillY;
    private float pillWidth;

    boolean isOpen() {
        return message != null && list.isOpen();
    }

    // react == null — реакции не ставятся, плашки нет
    void open(PrivateMessage message, List<ChatMenu.Item> items, Consumer<String> react, float mouseX, float mouseY,
              int areaWidth, int areaHeight) {
        this.message = message;
        this.react = react;
        float top = react == null ? mouseY : Math.max(mouseY, PILL_HEIGHT + GAP + 4);
        list.open(null, items, mouseX, top, areaWidth, areaHeight);
        pillWidth = Reactions.ALL.size() * CELL + PAD * 2;
        pillX = Math.max(4, Math.min(list.x(), areaWidth - pillWidth - 4));
        pillY = list.y() - GAP - PILL_HEIGHT;
    }

    void close() {
        message = null;
        list.close();
    }

    void draw(Gfx g, int mouseX, int mouseY) {
        if (!isOpen()) {
            return;
        }
        if (react != null) {
            drawPill(g, mouseX, mouseY);
        }
        list.draw(g, mouseX, mouseY);
    }

    private void drawPill(Gfx g, int mouseX, int mouseY) {
        float radius = PILL_HEIGHT / 2f;
        Draw.shadow(g, pillX, pillY + 2, pillWidth, PILL_HEIGHT, radius, 10, Theme.SHADOW);
        Draw.box(g, pillX, pillY, pillWidth, PILL_HEIGHT, radius, Theme.POPUP, Theme.BORDER_HOVER);
        for (int i = 0; i < Reactions.ALL.size(); i++) {
            String reaction = Reactions.ALL.get(i);
            float cellX = pillX + PAD + i * CELL;
            float cellY = pillY + PAD;
            boolean hover = mouseX >= cellX && mouseX < cellX + CELL && mouseY >= cellY && mouseY < cellY + CELL;
            if (reaction.equals(message.reaction())) {
                Draw.roundedRect(g, cellX, cellY, CELL, CELL, CELL / 2f, Theme.TILE_ON);
            } else if (hover) {
                Draw.roundedRect(g, cellX, cellY, CELL, CELL, CELL / 2f, Theme.CARD_HOVER);
            }
            float size = hover ? EMOJI + 2 : EMOJI;
            ReactionChips.emoji(g, reaction, cellX + (CELL - size) / 2, cellY + (CELL - size) / 2, Math.round(size));
        }
    }

    // false — клик мимо меню (оно закрыто, клик идёт дальше); true — выбрано что-то или пустое место меню
    boolean click(double mouseX, double mouseY) {
        if (!isOpen()) {
            return false;
        }
        if (react != null && mouseX >= pillX && mouseX < pillX + pillWidth && mouseY >= pillY && mouseY < pillY + PILL_HEIGHT) {
            int index = (int) Math.floor((mouseX - pillX - PAD) / CELL);
            if (index >= 0 && index < Reactions.ALL.size()) {
                String reaction = Reactions.ALL.get(index);
                // своя такая же — значит снять
                react.accept(reaction.equals(message.reaction()) ? null : reaction);
            }
            close();
            return true;
        }
        boolean inside = list.contains(mouseX, mouseY);
        list.click(mouseX, mouseY);
        close();
        return inside;
    }
}
