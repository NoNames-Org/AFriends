package dev.no.name.afriends.gui.friends;

/** Что частям окна мессенджера нужно от самого окна: открыть чат и управлять фокусом поля ввода. */
interface ChatNavigator {
    /** Открыть чат (null — никакой) и сбросить всё, что относилось к прежнему. */
    void select(String chat);

    void focusInput();

    void blurInput();

    /** Вернуть ввод текста полю сообщения после закрытия окошка, которое его забирало. */
    void refocusInput();
}
