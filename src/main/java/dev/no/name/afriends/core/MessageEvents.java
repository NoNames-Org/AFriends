package dev.no.name.afriends.core;

/**
 * Что происходит с перепиской: на это подписываются сохранение на диск и звук уведомления.
 * Одна точка для всех путей доставки (сеть, /msg сервера, AFriends), поэтому реагируют одинаково
 */
public interface MessageEvents {
    MessageEvents NONE = new MessageEvents() {
    };

    /** Переписка изменилась и её стоит сохранить. */
    default void changed() {
    }

    /** Новое непрочитанное сообщение в чате, который сейчас не открыт. */
    default void incoming(String chat) {
    }
}
