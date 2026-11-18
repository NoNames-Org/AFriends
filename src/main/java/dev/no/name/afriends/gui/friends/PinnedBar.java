package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.core.MessageReply;
import dev.no.name.afriends.core.PrivateMessage;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.icon.Icon;
import dev.no.name.afriends.gui.render.text.TextStyle;

import java.util.List;

/**
 * Закреплённое сообщение под шапкой переписки, как в Telegram: «Закреплённое сообщение» (если их несколько —
 * «… #N» и полоски слева) и начало текста. Клик — прокрутить к нему, и плашка переходит к предыдущему закрепу;
 * крестик — открепить показанное
 */
final class PinnedBar {
    static final int HEIGHT = 22;
    private static final int CLOSE = 12;

    sealed interface Click permits None, Jump, Unpin {
    }

    record None() implements Click {
    }

    record Jump(PrivateMessage message) implements Click {
    }

    record Unpin(PrivateMessage message) implements Click {
    }

    // какой по счёту с конца показан: 0 — последний закреплённый
    private int fromEnd;
    private List<PrivateMessage> shown = List.of();
    private float x;
    private float y;
    private float width;

    void reset() {
        fromEnd = 0;
    }

    void draw(Gfx g, List<PrivateMessage> pinned, float x, float y, float width, int mouseX, int mouseY) {
        this.shown = pinned;
        this.x = x;
        this.y = y;
        this.width = width;
        PrivateMessage current = current();
        if (current == null) {
            return;
        }
        Draw.rect(g, x, y, width, HEIGHT, Theme.CARD);
        Draw.rect(g, x, y + HEIGHT - 1, width, 1, Theme.BORDER);
        drawStripes(g, x + 8, y + 4, HEIGHT - 8);
        int index = pinned.size() - 1 - fromEnd;
        String title = pinned.size() == 1 || index == pinned.size() - 1 ? "Закреплённое сообщение" : "Закреплённое сообщение #" + (index + 1);
        float textX = x + 15;
        int textWidth = (int) (x + width - CLOSE - 10 - textX);
        Draw.text(g, Draw.trim(title, textWidth, TextStyle.SMALL), textX, y + 4, Theme.ACCENT, TextStyle.SMALL);
        Draw.text(g, Draw.trim(MessageReply.preview(current), textWidth, TextStyle.SMALL), textX, y + 12, Theme.TEXT_SOFT, TextStyle.SMALL);
        boolean hover = onClose(mouseX, mouseY);
        Draw.icon(g, Icon.CLOSE, closeX() + 2, y + (HEIGHT - Theme.ICON_SMALL) / 2f, Theme.ICON_SMALL, hover ? Theme.TEXT : Theme.TEXT_DIM);
    }

    // полоски слева: сколько закрепов и какой из них показан
    private void drawStripes(Gfx g, float stripeX, float top, float height) {
        int count = Math.min(shown.size(), 4);
        int active = Math.min(count - 1, shown.size() - 1 - fromEnd);
        float gap = 1;
        float each = (height - gap * (count - 1)) / count;
        for (int i = 0; i < count; i++) {
            int color = i == active ? Theme.ACCENT : Theme.TEXT_FAINT;
            Draw.roundedRect(g, stripeX, top + i * (each + gap), 2, each, 1, color);
        }
    }

    private PrivateMessage current() {
        if (shown.isEmpty()) {
            return null;
        }
        if (fromEnd >= shown.size()) {
            fromEnd = 0;
        }
        return shown.get(shown.size() - 1 - fromEnd);
    }

    private float closeX() {
        return x + width - CLOSE - 6;
    }

    private boolean onClose(double mouseX, double mouseY) {
        return mouseX >= closeX() && mouseX < closeX() + CLOSE && mouseY >= y && mouseY < y + HEIGHT;
    }

    Click click(double mouseX, double mouseY) {
        PrivateMessage current = current();
        if (current == null || mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + HEIGHT) {
            return new None();
        }
        if (onClose(mouseX, mouseY)) {
            return new Unpin(current);
        }
        // следующий клик — к предыдущему закрепу, после самого старого — снова к последнему
        fromEnd = (fromEnd + 1) % shown.size();
        return new Jump(current);
    }
}
