package dev.no.name.blooddesigner.api;

// что прятать на экране во время трансляции
@FunctionalInterface
public interface Privacy {
    // прятать текст сообщений в ЛС
    boolean hideMessages();
}
