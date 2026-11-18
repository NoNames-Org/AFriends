package dev.no.name.afriends.api;

import dev.no.name.afriends.compat.WidgetBase;
import net.minecraft.client.input.KeyEvent;

/** Панель аддона рядом с чатом: сам виджет и как ему достаются колесо, клавиши и потеря фокуса. */
public interface SidePanel {
    WidgetBase widget();

    /** true — колесо досталось панели, чат его не получит. */
    default boolean scroll(double mouseX, double mouseY, double amount) {
        return widget().isMouseOver(mouseX, mouseY);
    }

    /** true — клавишу забрала панель. */
    default boolean handleKey(KeyEvent event) {
        return false;
    }

    /** Клик мимо панели или чат закрылся: погасить своё поле ввода. */
    default void blur() {
    }

    /** Панель открыли или закрыли. */
    default void opened(boolean open) {
    }
}
