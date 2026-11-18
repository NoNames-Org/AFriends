package dev.no.name.afriends.api;

import java.util.Optional;

// проверка исходящего сообщения в ЛС
@FunctionalInterface
public interface SendCheck {
    // причина не отправлять или пусто, если можно; command — команда целиком, например «msg Ник текст»
    Optional<String> problem(String partner, String command);
}
