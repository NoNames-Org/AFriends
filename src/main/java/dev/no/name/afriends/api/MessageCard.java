package dev.no.name.afriends.api;

import dev.no.name.afriends.gui.render.icon.Icon;

// своя карточка вместо пузыря с текстом, например присланная метка
@FunctionalInterface
public interface MessageCard {
    // null — обычный пузырь
    Card card(String partner, String text, boolean fromMe);

    // button null — без кнопки; done — кнопка уже нажата и неактивна
    record Card(Icon icon, String title, String subtitle, String button, boolean done, Runnable action) {
    }
}
