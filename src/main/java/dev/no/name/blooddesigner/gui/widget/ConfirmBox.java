package dev.no.name.blooddesigner.gui.widget;

import dev.no.name.blooddesigner.gui.render.TextWrap;
import dev.no.name.blooddesigner.compat.Gfx;
import dev.no.name.blooddesigner.gui.Theme;
import dev.no.name.blooddesigner.gui.render.Draw;
import dev.no.name.blooddesigner.gui.render.icon.Icon;
import dev.no.name.blooddesigner.gui.render.text.TextStyle;

import java.util.List;
import java.util.function.Consumer;

/**
 * Окошко подтверждения посреди переписки, как в Telegram: вопрос, при надобности галочка
 * («Также удалить для …», «Также закрепить для …») и две кнопки. Enter — подтвердить, Esc или клик мимо — отмена
 */
public final class ConfirmBox {
    private static final int WIDTH = 210;
    private static final int PAD = 10;
    private static final int LINE = 10;
    private static final int CHECK = 9;
    private static final int CHECK_ROW = 16;
    private static final int BUTTON_HEIGHT = 16;
    private static final int BUTTON_WIDTH = 70;

    // check == null — без галочки; done получает, стоит ли галочка
    public record Request(String text, String check, boolean checkedByDefault, String confirm, boolean danger, Consumer<Boolean> done) {
    }

    private Request request;
    private boolean checked;
    private List<String> lines = List.of();
    private float x;
    private float y;
    private float height;

    public boolean isOpen() {
        return request != null;
    }

    public void open(Request request, int areaWidth, int areaHeight) {
        this.request = request;
        this.checked = request.checkedByDefault();
        this.lines = TextWrap.lines(request.text(), WIDTH - PAD * 2);
        height = PAD + lines.size() * LINE + (request.check() != null ? 6 + CHECK_ROW : 0) + 10 + BUTTON_HEIGHT + PAD;
        x = (areaWidth - WIDTH) / 2f;
        y = (areaHeight - height) / 2f;
    }

    public void close() {
        request = null;
    }

    public void confirm() {
        if (request != null) {
            Request done = request;
            request = null;
            done.done().accept(checked);
        }
    }

    public void draw(Gfx g, int areaWidth, int areaHeight, int mouseX, int mouseY) {
        if (request == null) {
            return;
        }
        Draw.roundedRect(g, 0, 0, areaWidth, areaHeight, Theme.PANEL_RADIUS, 0x66000000);
        Draw.shadow(g, x, y + 3, WIDTH, height, Theme.RADIUS + 3, 16, Theme.SHADOW);
        Draw.box(g, x, y, WIDTH, height, Theme.RADIUS + 3, Theme.POPUP, Theme.BORDER_HOVER);
        float lineY = y + PAD;
        for (String line : lines) {
            Draw.text(g, line, x + PAD, lineY, Theme.TEXT);
            lineY += LINE;
        }
        if (request.check() != null) {
            float rowY = lineY + 6;
            boolean hover = inside(mouseX, mouseY, x + PAD - 2, rowY, WIDTH - PAD * 2 + 4, CHECK_ROW);
            float boxY = rowY + (CHECK_ROW - CHECK) / 2f;
            Draw.box(g, x + PAD, boxY, CHECK, CHECK, 2, checked ? Theme.ACCENT : Theme.CARD, hover ? Theme.BORDER_SELECTED : Theme.BORDER_HOVER);
            if (checked) {
                Draw.icon(g, Icon.CHECK, x + PAD + 1, boxY + 1, CHECK - 2, Theme.ACCENT_TEXT);
            }
            Draw.text(g, Draw.trim(request.check(), WIDTH - PAD * 2 - CHECK - 6), x + PAD + CHECK + 6, rowY + 4,
                    hover ? Theme.TEXT : Theme.TEXT_SOFT);
        }
        float buttonY = y + height - PAD - BUTTON_HEIGHT;
        drawButton(g, "Отмена", cancelX(), buttonY, false, mouseX, mouseY);
        drawButton(g, request.confirm(), confirmX(), buttonY, request.danger(), mouseX, mouseY);
    }

    private float confirmX() {
        return x + WIDTH - PAD - BUTTON_WIDTH;
    }

    private float cancelX() {
        return confirmX() - 6 - BUTTON_WIDTH;
    }

    private static void drawButton(Gfx g, String label, float bx, float by, boolean danger, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, bx, by, BUTTON_WIDTH, BUTTON_HEIGHT);
        Draw.roundedRect(g, bx, by, BUTTON_WIDTH, BUTTON_HEIGHT, Theme.RADIUS, hover ? Theme.CARD_HOVER : Theme.CARD);
        int color = danger ? Theme.DANGER : hover ? Theme.TEXT : Theme.TEXT_SOFT;
        Draw.text(g, label, bx + (BUTTON_WIDTH - Draw.width(label)) / 2f, by + 4, color, TextStyle.BODY_MEDIUM);
    }

    // окно модальное: любой клик поглощается; мимо окна — отмена
    public void click(double mouseX, double mouseY) {
        if (request == null) {
            return;
        }
        if (!inside(mouseX, mouseY, x, y, WIDTH, height)) {
            close();
            return;
        }
        float buttonY = y + height - PAD - BUTTON_HEIGHT;
        if (inside(mouseX, mouseY, confirmX(), buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            confirm();
        } else if (inside(mouseX, mouseY, cancelX(), buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            close();
        } else if (request.check() != null
                && inside(mouseX, mouseY, x + PAD - 2, y + PAD + lines.size() * LINE + 6, WIDTH - PAD * 2 + 4, CHECK_ROW)) {
            checked = !checked;
        }
    }

    private static boolean inside(double px, double py, float bx, float by, float bw, float bh) {
        return px >= bx && px < bx + bw && py >= by && py < by + bh;
    }
}
