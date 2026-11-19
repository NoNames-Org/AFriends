package dev.no.name.afriends.gui.friends;

/**
 * Разметка окна мессенджера на холсте 470×290: рейка слева, список чатов, переписка. Общая для всех частей окна,
 * чтобы размеры не расходились
 */
final class MessengerLayout {
    static final int WIDTH = 470;
    static final int HEIGHT = 290;
    static final int SCREEN_MARGIN = 20;
    static final int RAIL_WIDTH = 32;
    static final int LIST_X = RAIL_WIDTH;
    static final int LIST_WIDTH = 150;
    static final int CHAT_X = LIST_X + LIST_WIDTH;
    static final int CHAT_WIDTH = WIDTH - CHAT_X;
    static final int PAD = 8;
    static final int RAIL_BUTTON = 22;
    static final int RAIL_STEP = 27;
    static final int FIELD_HEIGHT = 18;
    static final int ROW_HEIGHT = 30;
    static final int ROW_FACE = 20;
    static final int HEADER_HEIGHT = 40;
    static final int HEADER_FACE = 24;
    static final int INPUT_HEIGHT = 20;
    // сколько строк поле ввода показывает, прежде чем перестать расти вверх
    static final int MAX_INPUT_LINES = 6;
    static final int SEND_WIDTH = 26;
    // полоса ввода как в Telegram: скрепка слева, поле, значки справа; «отправить» — рядом
    static final int BAR_X = CHAT_X + PAD;
    static final int BAR_WIDTH = CHAT_WIDTH - PAD * 2 - SEND_WIDTH - 4;
    static final int BAR_ICON = 16;
    static final int SEPARATOR_HEIGHT = 18;
    static final int SCROLL_STEP = 16;
    static final int MAX_TEXT = 200;
    static final int DANGER = 0xFFFF6B6B;
    /** Низ полосы ввода без многострочного роста. */
    static final int BAR_Y = HEIGHT - PAD - INPUT_HEIGHT;

    private MessengerLayout() {
    }

    static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /** Верх списка чатов: под полем поиска. */
    static int listTop() {
        return PAD + FIELD_HEIGHT + 6;
    }
}
