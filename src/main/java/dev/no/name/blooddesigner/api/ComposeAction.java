package dev.no.name.blooddesigner.api;

import dev.no.name.blooddesigner.gui.render.icon.Icon;

import java.util.List;

// кнопка у поля ввода в ЛС: по клику список вариантов, выбранный уходит сообщением
public interface ComposeAction {
    Icon icon();

    // заголовок списка
    String title(String partner);

    // пусто — показать empty(partner)
    List<Option> options(String partner);

    // почему вариантов нет
    String empty(String partner);

    record Option(Icon icon, String label, String text) {
    }
}
