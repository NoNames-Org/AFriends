package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.core.PrivateMessage;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.icon.Icon;
import dev.no.name.afriends.gui.render.text.TextStyle;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Выделение сообщений, как в Telegram: входит через «Выделить» в меню сообщения, клик по сообщению отмечает или
 * снимает его. Вместо шапки переписки — «Выбрано: N» и кнопки «Переслать», «Удалить», «Отмена».
 * Сняли последнее — выделение заканчивается само
 */
final class Selection {
    static final int GUTTER = 16;
    private static final int CIRCLE = 10;
    private static final int BUTTON_HEIGHT = 18;
    private static final int DANGER = 0xFFFF6B6B;

    enum Action { NONE, FORWARD, DELETE, CANCEL }

    private record Button(Action action, Icon icon, String label) {
    }

    private static final List<Button> BUTTONS = List.of(
            new Button(Action.FORWARD, Icon.SHARE, "Переслать"),
            new Button(Action.DELETE, Icon.TRASH, "Удалить"),
            new Button(Action.CANCEL, Icon.CLOSE, "Отмена"));

    private final Set<String> keys = new LinkedHashSet<>();
    private float barRight;
    private float barY;

    boolean isActive() {
        return !keys.isEmpty();
    }

    void start(PrivateMessage message) {
        keys.clear();
        keys.add(message.key());
    }

    void clear() {
        keys.clear();
    }

    boolean contains(PrivateMessage message) {
        return keys.contains(message.key());
    }

    void toggle(PrivateMessage message) {
        if (!keys.remove(message.key())) {
            keys.add(message.key());
        }
    }

    // выделенные по порядку переписки; удалённые тем временем пропадают
    List<PrivateMessage> messages(List<PrivateMessage> chat) {
        List<PrivateMessage> result = chat.stream().filter(this::contains).toList();
        keys.retainAll(result.stream().map(PrivateMessage::key).toList());
        return result;
    }

    // полоса вместо шапки переписки
    void drawBar(Gfx g, float x, float y, float width, float height, int mouseX, int mouseY) {
        barRight = x + width;
        barY = y + (height - BUTTON_HEIGHT) / 2f;
        Draw.text(g, "Выбрано: " + keys.size(), x, y + (height - 10) / 2f, Theme.TEXT, TextStyle.TITLE);
        float buttonX = barRight;
        for (int i = BUTTONS.size() - 1; i >= 0; i--) {
            Button button = BUTTONS.get(i);
            float buttonWidth = buttonWidth(button);
            buttonX -= buttonWidth;
            boolean hover = inside(mouseX, mouseY, buttonX, barY, buttonWidth, BUTTON_HEIGHT);
            Draw.roundedRect(g, buttonX, barY, buttonWidth, BUTTON_HEIGHT, Theme.RADIUS, hover ? Theme.CARD_HOVER : Theme.CARD);
            int color = button.action() == Action.DELETE ? DANGER : hover ? Theme.TEXT : Theme.TEXT_SOFT;
            Draw.icon(g, button.icon(), buttonX + 5, barY + (BUTTON_HEIGHT - Theme.ICON_SMALL) / 2f, Theme.ICON_SMALL, color);
            Draw.text(g, button.label(), buttonX + 5 + Theme.ICON_SMALL + 4, barY + 5, color);
            buttonX -= 4;
        }
    }

    Action click(double mouseX, double mouseY) {
        float buttonX = barRight;
        for (int i = BUTTONS.size() - 1; i >= 0; i--) {
            Button button = BUTTONS.get(i);
            float buttonWidth = buttonWidth(button);
            buttonX -= buttonWidth;
            if (inside(mouseX, mouseY, buttonX, barY, buttonWidth, BUTTON_HEIGHT)) {
                return button.action();
            }
            buttonX -= 4;
        }
        return Action.NONE;
    }

    // кружок слева от сообщения: пустой или с галочкой
    void drawMark(Gfx g, PrivateMessage message, float x, float centerY) {
        float y = centerY - CIRCLE / 2f;
        if (contains(message)) {
            Draw.roundedRect(g, x, y, CIRCLE, CIRCLE, CIRCLE / 2f, Theme.ACCENT);
            Draw.icon(g, Icon.CHECK, x + 2, y + 2, CIRCLE - 4, Theme.ACCENT_TEXT);
        } else {
            Draw.box(g, x, y, CIRCLE, CIRCLE, CIRCLE / 2f, 0x00000000, Theme.TEXT_DIM);
        }
    }

    private static float buttonWidth(Button button) {
        return 5 + Theme.ICON_SMALL + 4 + Draw.width(button.label()) + 6;
    }

    private static boolean inside(double px, double py, float bx, float by, float bw, float bh) {
        return px >= bx && px < bx + bw && py >= by && py < by + bh;
    }
}
