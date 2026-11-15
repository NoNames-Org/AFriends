package dev.no.name.blooddesigner.core;

import java.util.List;

/** Реакции на сообщения, как в Telegram: у каждой стороны переписки — одна своя. Набор закрытый, картинки — Twemoji. */
public final class Reactions {
    public static final List<String> ALL = List.of("like", "heart", "laugh", "wow", "sad", "fire", "pray", "dislike");

    private Reactions() {
    }

    public static boolean valid(String reaction) {
        return reaction != null && ALL.contains(reaction);
    }
}
