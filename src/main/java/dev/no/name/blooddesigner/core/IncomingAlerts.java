package dev.no.name.blooddesigner.core;

import java.util.function.Consumer;

/**
 * Новое непрочитанное сообщение в чате, который сейчас не открыт: сюда подписывается звук уведомления.
 * Одна точка для всех путей доставки (сеть, /msg сервера, BloodDesigner), поэтому звенит одинаково
 */
public final class IncomingAlerts {
    private static Consumer<String> listener = chat -> { };

    private IncomingAlerts() {
    }

    public static void setListener(Consumer<String> value) {
        listener = value;
    }

    static void fire(String chat) {
        listener.accept(chat);
    }
}
