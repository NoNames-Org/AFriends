package dev.no.name.afriends.gui.friends;

import com.mojang.blaze3d.platform.InputConstants;
import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.core.NotifySettings;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.text.TextStyle;
import dev.no.name.afriends.gui.settings.ToggleList;
import dev.no.name.afriends.gui.sound.UiSound;
import dev.no.name.afriends.gui.sound.UiSounds;

/**
 * Настройки мессенджера поверх переписки (шестерёнка внизу слева): звук, всплывающие уведомления, отметки о прочтении
 * и кто может писать. Последнее живёт на сервере сети — без сети оно недоступно.
 */
final class MessengerSettingsPanel {
    private static final int WIDTH = 240;
    private static final int PAD = 8;
    private static final int TITLE = 16;
    private static final int ROW = 26;
    private static final int SWITCH_WIDTH = 22;

    /** Что хранится на сервере сети; available false — сеть не подключена. */
    interface Host {
        boolean available();

        boolean dmFromEveryone();

        void setDmFromEveryone(boolean everyone);
    }

    private enum Row { SOUND, TOASTS, RECEIPTS, DM }

    private final NotifySettings notify;
    private final Host host;
    private boolean open;
    private float x;
    private float y;
    private float height;

    MessengerSettingsPanel(NotifySettings notify, Host host) {
        this.notify = notify;
        this.host = host;
    }

    boolean isOpen() {
        return open;
    }

    void open(int areaWidth, int areaHeight) {
        open = true;
        height = PAD * 2 + TITLE + Row.values().length * ROW - 4;
        x = (areaWidth - WIDTH) / 2f;
        y = (areaHeight - height) / 2f;
    }

    void close() {
        open = false;
    }

    private float rowY(Row row) {
        return y + PAD + TITLE + row.ordinal() * ROW;
    }

    void draw(Gfx g, int mouseX, int mouseY) {
        if (!open) {
            return;
        }
        Draw.shadow(g, x, y + 2, WIDTH, height, Theme.PANEL_RADIUS, 12, Theme.SHADOW);
        Draw.box(g, x, y, WIDTH, height, Theme.PANEL_RADIUS, Theme.POPUP, Theme.BORDER_HOVER);
        Draw.text(g, "Настройки мессенджера", x + PAD, y + PAD, Theme.TEXT, TextStyle.BODY_MEDIUM);
        float textWidth = WIDTH - PAD * 2 - SWITCH_WIDTH - 8;
        drawToggle(g, Row.SOUND, "Звук сообщений", "У отдельного чата — в его меню", notify.soundEnabled(), textWidth);
        drawToggle(g, Row.TOASTS, "Уведомления", "Карточка о новом сообщении в углу экрана", notify.toastsEnabled(), textWidth);
        drawToggle(g, Row.RECEIPTS, "Отметки о прочтении", "Выключишь — собеседники не увидят «прочитано»",
                notify.readReceipts(), textWidth);
        boolean network = host.available();
        drawToggle(g, Row.DM, "Писать мне могут все", network ? "Выключено — только друзья" : "Нужна сеть AFriends",
                network && host.dmFromEveryone(), textWidth);
    }

    private void drawToggle(Gfx g, Row row, String title, String hint, boolean on, float textWidth) {
        float rowY = rowY(row);
        Draw.text(g, Draw.trim(title, (int) textWidth), x + PAD, rowY, Theme.TEXT);
        Draw.text(g, Draw.trim(hint, (int) textWidth), x + PAD, rowY + 11, Theme.TEXT_DIM, TextStyle.SMALL);
        ToggleList.drawSwitch(g, x + WIDTH - PAD - SWITCH_WIDTH, rowY + 4, on);
    }

    /** true — клик внутри окна (обработан); клик мимо закрывает окно. */
    boolean click(double mouseX, double mouseY) {
        if (!open) {
            return false;
        }
        if (mouseX < x || mouseX >= x + WIDTH || mouseY < y || mouseY >= y + height) {
            close();
            return true;
        }
        Row row = rowAt(mouseY);
        if (row == null) {
            return true;
        }
        switch (row) {
            case SOUND -> notify.toggleSound();
            case RECEIPTS -> notify.toggleReadReceipts();
            case TOASTS -> notify.toggleToasts();
            case DM -> {
                if (host.available()) {
                    host.setDmFromEveryone(!host.dmFromEveryone());
                }
            }
        }
        UiSounds.play(UiSound.CLICK);
        return true;
    }

    private Row rowAt(double mouseY) {
        for (Row row : Row.values()) {
            float top = rowY(row);
            if (mouseY >= top - 2 && mouseY < top + ROW - 4) {
                return row;
            }
        }
        return null;
    }

    /** true — клавишу забрало окно (Esc закрывает). */
    boolean key(int key) {
        if (!open) {
            return false;
        }
        if (key == InputConstants.KEY_ESCAPE) {
            close();
        }
        return true;
    }

    /** Полей ввода нет: набранный текст просто поглощается, пока окно открыто. */
    boolean type(String text) {
        return open;
    }
}
