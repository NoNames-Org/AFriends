package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.render.Colors;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.icon.Icon;

import static dev.no.name.afriends.gui.friends.MessengerLayout.HEIGHT;
import static dev.no.name.afriends.gui.friends.MessengerLayout.PAD;
import static dev.no.name.afriends.gui.friends.MessengerLayout.RAIL_BUTTON;
import static dev.no.name.afriends.gui.friends.MessengerLayout.RAIL_STEP;
import static dev.no.name.afriends.gui.friends.MessengerLayout.RAIL_WIDTH;
import static dev.no.name.afriends.gui.friends.MessengerLayout.inside;

/** Рейка слева: «Прочитать всё», «Все чаты», «Избранное», «Новая группа», внизу — настройки мессенджера. */
final class RailView {
    private static final int BUTTON_X = (RAIL_WIDTH - RAIL_BUTTON) / 2;

    enum Action { READ_ALL, ALL_CHATS, NOTES, NEW_GROUP }

    /** notes — открыто «Избранное»; settingsOpen — открыты настройки. */
    void draw(Gfx g, int mouseX, int mouseY, boolean notes, boolean settingsOpen) {
        Action[] actions = Action.values();
        for (int i = 0; i < actions.length; i++) {
            int y = buttonY(i);
            boolean active = actions[i] == Action.NOTES ? notes : actions[i] == Action.ALL_CHATS && !notes;
            drawButton(g, icon(actions[i]), y, inside(mouseX, mouseY, BUTTON_X, y, RAIL_BUTTON, RAIL_BUTTON), active);
            if (i == 0) {
                Draw.rect(g, 8, y + RAIL_BUTTON + 3, RAIL_WIDTH - 16, 1, Theme.border());
            }
        }
        // настройки мессенджера — внизу, как в Telegram
        int y = settingsY();
        drawButton(g, Icon.SETTINGS, y, inside(mouseX, mouseY, BUTTON_X, y, RAIL_BUTTON, RAIL_BUTTON), settingsOpen);
    }

    void drawTooltip(Gfx g, int mouseX, int mouseY) {
        if (settingsAt(mouseX, mouseY)) {
            tooltip(g, "Настройки мессенджера", settingsY());
        }
        Action[] actions = Action.values();
        for (int i = 0; i < actions.length; i++) {
            int y = buttonY(i);
            if (inside(mouseX, mouseY, BUTTON_X, y, RAIL_BUTTON, RAIL_BUTTON)) {
                tooltip(g, label(actions[i]), y);
            }
        }
    }

    /** Кнопка рейки под курсором или null. */
    Action actionAt(double x, double y) {
        Action[] actions = Action.values();
        for (int i = 0; i < actions.length; i++) {
            if (inside(x, y, BUTTON_X, buttonY(i), RAIL_BUTTON, RAIL_BUTTON)) {
                return actions[i];
            }
        }
        return null;
    }

    boolean settingsAt(double x, double y) {
        return inside(x, y, BUTTON_X, settingsY(), RAIL_BUTTON, RAIL_BUTTON);
    }

    private static void drawButton(Gfx g, Icon icon, int y, boolean hovered, boolean active) {
        int fill = active ? Theme.accent() : hovered ? Theme.cardHover() : Colors.fade(Theme.card(), 0);
        Draw.roundedRect(g, BUTTON_X, y, RAIL_BUTTON, RAIL_BUTTON, Theme.RADIUS, fill);
        int color = active ? Theme.accentText() : hovered ? Theme.text() : Theme.textSoft();
        Draw.icon(g, icon, BUTTON_X + (RAIL_BUTTON - Theme.ICON) / 2f, y + (RAIL_BUTTON - Theme.ICON) / 2f,
                Theme.ICON, color);
    }

    private static void tooltip(Gfx g, String label, int y) {
        float width = Draw.width(label) + 12;
        Draw.shadow(g, RAIL_WIDTH + 2, y + 4, width, 16, Theme.RADIUS, 8, Theme.shadow());
        Draw.box(g, RAIL_WIDTH + 2, y + 4, width, 16, Theme.RADIUS, Theme.popup(), Theme.borderHover());
        Draw.text(g, label, RAIL_WIDTH + 8, y + 8, Theme.text());
    }

    private static int buttonY(int index) {
        return PAD + index * RAIL_STEP;
    }

    private static int settingsY() {
        return HEIGHT - PAD - RAIL_BUTTON;
    }

    private static Icon icon(Action action) {
        return switch (action) {
            case READ_ALL -> Icon.CHECK_SQUARE;
            case ALL_CHATS -> Icon.MESSAGE;
            case NOTES -> Icon.HEART;
            case NEW_GROUP -> Icon.USERS;
        };
    }

    private static String label(Action action) {
        return switch (action) {
            case READ_ALL -> "Прочитать всё";
            case ALL_CHATS -> "Все чаты";
            case NOTES -> "Избранное";
            case NEW_GROUP -> "Новая группа";
        };
    }
}
