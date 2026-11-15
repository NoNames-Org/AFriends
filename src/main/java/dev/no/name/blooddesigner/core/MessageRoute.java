package dev.no.name.blooddesigner.core;

import java.util.Optional;
import java.util.function.Consumer;

// Куда уходит ЛС: окна не знают, сеть это или /msg сервера. Всё на потоке игры
public interface MessageRoute {
    // пусто — ушло в обработку; иначе причина, почему не ушло. Поздний отказ сети приходит в failed
    Optional<String> send(String partner, String text, Consumer<String> failed);

    // друг по сети: писать можно, даже если он на другом сервере
    boolean viaNetwork(String partner);

    // строка под ником в шапке переписки; null — своя подпись BloodDesigner
    String headerLine(String partner);
}
