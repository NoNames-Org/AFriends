package dev.no.name.afriends.api;

// что прятать на экране во время трансляции
@FunctionalInterface
public interface Privacy {
    // прятать текст сообщений в ЛС
    boolean hideMessages();
}
