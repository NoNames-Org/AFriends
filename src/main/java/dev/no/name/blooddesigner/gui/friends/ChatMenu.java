package dev.no.name.blooddesigner.gui.friends;

import dev.no.name.blooddesigner.compat.Gfx;
import dev.no.name.blooddesigner.gui.Theme;
import dev.no.name.blooddesigner.gui.animation.Animated;
import dev.no.name.blooddesigner.gui.animation.Motion;
import dev.no.name.blooddesigner.gui.render.Colors;
import dev.no.name.blooddesigner.gui.render.Draw;
import dev.no.name.blooddesigner.gui.render.icon.Icon;
import dev.no.name.blooddesigner.gui.render.text.TextStyle;

import java.util.List;

final class ChatMenu {
    static final int WIDTH = 150;
    private static final int PADDING = 4;
    private static final int HEADER_HEIGHT = 15;
    private static final int ITEM_HEIGHT = 16;
    private static final int DANGER = 0xFFFF6B6B;

    record Item(Icon icon, String label, boolean danger, Runnable action) {
    }

    private final Animated shown = new Animated(0, Motion.FAST);
    private String title;
    private List<Item> items = List.of();
    private float x;
    private float y;
    private boolean open;

    void open(String menuTitle, List<Item> menuItems, float mouseX, float mouseY, int canvasWidth, int canvasHeight) {
        title = menuTitle;
        items = List.copyOf(menuItems);
        x = Math.min(mouseX, canvasWidth - WIDTH - 4);
        y = Math.min(mouseY, canvasHeight - height() - 4);
        open = true;
        shown.snap(0);
        shown.setTarget(1);
    }

    void close() {
        open = false;
    }

    boolean isOpen() {
        return open;
    }

    private int height() {
        return HEADER_HEIGHT + items.size() * ITEM_HEIGHT + PADDING * 2;
    }

    void draw(Gfx g, int mouseX, int mouseY) {
        if (!open) {
            return;
        }
        float progress = shown.get();
        Draw.pushAlpha(progress);
        Draw.pushOffset(g, 0, (1 - progress) * -3);
        Draw.shadow(g, x, y + 2, WIDTH, height(), Theme.RADIUS + 2, 12, Theme.SHADOW);
        Draw.box(g, x, y, WIDTH, height(), Theme.RADIUS + 2, Theme.POPUP, Theme.BORDER_HOVER);
        Draw.text(g, Draw.trim(title, WIDTH - PADDING * 4), x + PADDING * 2, y + PADDING + 3, Theme.TEXT_DIM, TextStyle.SMALL);
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            float itemX = x + PADDING;
            float itemY = itemY(i);
            float itemWidth = WIDTH - PADDING * 2;
            boolean hovered = mouseX >= itemX && mouseX < itemX + itemWidth && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
            if (hovered) {
                Draw.roundedRect(g, itemX, itemY, itemWidth, ITEM_HEIGHT, Theme.RADIUS, Theme.CARD_HOVER);
            }
            int color = item.danger() ? DANGER : Colors.mix(Theme.TEXT_SOFT, Theme.TEXT, hovered ? 1 : 0);
            Draw.icon(g, item.icon(), itemX + 5, itemY + (ITEM_HEIGHT - Theme.ICON_SMALL) / 2f, Theme.ICON_SMALL, color);
            Draw.text(g, item.label(), itemX + 5 + Theme.ICON_SMALL + 6, itemY + (ITEM_HEIGHT - 8) / 2f, color);
        }
        Draw.popOffset(g);
        Draw.popAlpha();
    }

    private float itemY(int index) {
        return y + PADDING + HEADER_HEIGHT + index * ITEM_HEIGHT;
    }

    boolean click(double mouseX, double mouseY) {
        if (!open) {
            return false;
        }
        open = false;
        if (mouseX < x || mouseX >= x + WIDTH) {
            return true;
        }
        for (int i = 0; i < items.size(); i++) {
            float itemY = itemY(i);
            if (mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT) {
                items.get(i).action().run();
                return true;
            }
        }
        return true;
    }
}
