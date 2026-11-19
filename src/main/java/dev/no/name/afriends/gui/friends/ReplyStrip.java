package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.core.MessageReply;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.icon.Icon;
import dev.no.name.afriends.gui.render.text.TextStyle;

/**
 * «Ответ …» над полем ввода, как в Telegram: значок, чьё сообщение, начало текста и крестик.
 * Клик по полосе — прокрутить к исходному, крестик или Esc — отменить ответ
 */
final class ReplyStrip {
    static final int HEIGHT = 22;
    private static final int CLOSE = 12;

    enum Click { NONE, JUMP, CANCEL }

    private MessageReply reply;
    private float x;
    private float y;
    private float width;

    boolean isActive() {
        return reply != null;
    }

    MessageReply reply() {
        return reply;
    }

    void start(MessageReply reply) {
        this.reply = reply;
    }

    void clear() {
        reply = null;
    }

    void draw(Gfx g, float x, float y, float width, int mouseX, int mouseY) {
        this.x = x;
        this.y = y;
        this.width = width;
        if (reply == null) {
            return;
        }
        Draw.box(g, x, y, width, HEIGHT, Theme.RADIUS, Theme.card(), Theme.border());
        Draw.icon(g, Icon.UNDO, x + 6, y + (HEIGHT - Theme.ICON) / 2f, Theme.ICON, Theme.accent());
        Draw.roundedRect(g, x + 22, y + 4, 2, HEIGHT - 8, 1, Theme.accent());
        float textX = x + 28;
        int textWidth = (int) (x + width - CLOSE - 8 - textX);
        Draw.text(g, Draw.trim("Ответ " + reply.author(), textWidth, TextStyle.SMALL), textX, y + 4, Theme.accent(), TextStyle.SMALL);
        Draw.text(g, Draw.trim(reply.snippet(), textWidth, TextStyle.SMALL), textX, y + 12, Theme.textSoft(), TextStyle.SMALL);
        boolean hover = onClose(mouseX, mouseY);
        Draw.icon(g, Icon.CLOSE, closeX() + 2, y + (HEIGHT - Theme.ICON_SMALL) / 2f, Theme.ICON_SMALL, hover ? Theme.text() : Theme.textDim());
    }

    private float closeX() {
        return x + width - CLOSE - 4;
    }

    private boolean onClose(double mouseX, double mouseY) {
        return mouseX >= closeX() && mouseX < closeX() + CLOSE && mouseY >= y && mouseY < y + HEIGHT;
    }

    Click click(double mouseX, double mouseY) {
        if (reply == null || mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + HEIGHT) {
            return Click.NONE;
        }
        if (onClose(mouseX, mouseY)) {
            clear();
            return Click.CANCEL;
        }
        return Click.JUMP;
    }
}
