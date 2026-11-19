package dev.no.name.afriends.core;

import java.util.function.Consumer;

/** Кто хочет знать, что игрок сам добавил или удалил друга (аддоны). */
public interface FriendEvents {
    FriendEvents NONE = new FriendEvents() {
        @Override
        public void added(String name, Consumer<String> notice) {
        }

        @Override
        public void removed(String name) {
        }
    };

    /** notice — куда показать ответ подписчика игроку. */
    void added(String name, Consumer<String> notice);

    void removed(String name);
}
