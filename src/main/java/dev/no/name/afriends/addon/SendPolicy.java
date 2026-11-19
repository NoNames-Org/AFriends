package dev.no.name.afriends.addon;

import java.util.Optional;

/** Можно ли отправить сообщение: аддоны могут запретить отправку и объяснить почему. */
public interface SendPolicy {
    SendPolicy ALLOW_ALL = (partner, command) -> Optional.empty();

    /** Пусто — можно; иначе причина отказа для игрока. */
    Optional<String> sendProblem(String partner, String command);
}
