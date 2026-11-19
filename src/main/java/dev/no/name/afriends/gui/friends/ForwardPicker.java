package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.core.PrivateMessage;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.text.TextStyle;

import java.util.List;
import java.util.function.Function;

/**
 * Окно «Переслать в…» посреди переписки: список чатов (Избранное, AFriends, переписки), колёсико листает.
 * Клик по чату — переслать туда, клик мимо или Esc — закрыть
 */
final class ForwardPicker {
    private static final int WIDTH = 170;
    private static final int ROW = 20;
    private static final int FACE = 14;
    private static final int PAD = 5;
    private static final int TITLE = 14;
    private static final int VISIBLE = 8;

    // как нарисовать значок чата: его рисует экран переписки
    interface TilePainter {
        void paint(Gfx g, String entry, float x, float y, int size);
    }

    private List<PrivateMessage> forwarded = List.of();
    private List<String> entries = List.of();
    private int scroll;
    private float x;
    private float y;
    private float height;

    boolean isOpen() {
        return !forwarded.isEmpty();
    }

    // что пересылаем: одно сообщение из меню или выделенные
    List<PrivateMessage> messages() {
        return forwarded;
    }

    void open(List<PrivateMessage> messages, List<String> entries, int areaWidth, int areaHeight) {
        this.forwarded = List.copyOf(messages);
        this.entries = List.copyOf(entries);
        this.scroll = 0;
        height = PAD * 2 + TITLE + Math.min(VISIBLE, Math.max(1, entries.size())) * ROW;
        x = (areaWidth - WIDTH) / 2f;
        y = (areaHeight - height) / 2f;
    }

    void close() {
        forwarded = List.of();
    }

    void draw(Gfx g, int mouseX, int mouseY, Function<String, String> names, TilePainter tiles) {
        if (forwarded.isEmpty()) {
            return;
        }
        Draw.shadow(g, x, y + 2, WIDTH, height, Theme.RADIUS + 2, 12, Theme.shadow());
        Draw.box(g, x, y, WIDTH, height, Theme.RADIUS + 2, Theme.popup(), Theme.border());
        String title = forwarded.size() == 1 ? "Переслать в…" : "Переслать " + forwarded.size() + " в…";
        Draw.text(g, title, x + PAD, y + PAD, Theme.textSoft(), TextStyle.SMALL);
        if (entries.isEmpty()) {
            Draw.textCentered(g, "Некуда пересылать", x + WIDTH / 2f, y + PAD + TITLE + 6, Theme.textDim());
            return;
        }
        for (int i = scroll; i < entries.size() && i < scroll + VISIBLE; i++) {
            float rowY = y + PAD + TITLE + (i - scroll) * ROW;
            boolean hover = mouseX >= x + PAD && mouseX < x + WIDTH - PAD && mouseY >= rowY && mouseY < rowY + ROW;
            if (hover) {
                Draw.roundedRect(g, x + PAD - 2, rowY, WIDTH - PAD * 2 + 4, ROW, Theme.RADIUS, Theme.cardHover());
            }
            tiles.paint(g, entries.get(i), x + PAD, rowY + (ROW - FACE) / 2f, FACE);
            Draw.text(g, Draw.trim(names.apply(entries.get(i)), WIDTH - PAD * 2 - FACE - 8), x + PAD + FACE + 6, rowY + 6, Theme.text());
        }
    }

    // чат, в который пересылать; null — мимо окна (его закрывают) или в пустое место окна
    String click(double mouseX, double mouseY) {
        if (forwarded.isEmpty() || mouseX < x || mouseX >= x + WIDTH || mouseY < y || mouseY >= y + height) {
            return null;
        }
        int index = scroll + (int) Math.floor((mouseY - y - PAD - TITLE) / ROW);
        return mouseY >= y + PAD + TITLE && index >= 0 && index < entries.size() ? entries.get(index) : "";
    }

    boolean contains(double mouseX, double mouseY) {
        return !forwarded.isEmpty() && mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + height;
    }

    boolean scroll(double mouseX, double mouseY, double amount) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        scroll = Math.max(0, Math.min(Math.max(0, entries.size() - VISIBLE), scroll + (amount < 0 ? 1 : -1)));
        return true;
    }
}
