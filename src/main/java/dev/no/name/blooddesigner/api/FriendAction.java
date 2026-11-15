package dev.no.name.blooddesigner.api;

import dev.no.name.blooddesigner.gui.render.icon.Icon;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

// пункт в меню друга; встаёт над встроенными пунктами
public record FriendAction(Icon icon, Function<String, String> label, Consumer<String> action, Predicate<String> visible,
                           boolean danger) {
}
