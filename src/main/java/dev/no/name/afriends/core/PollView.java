package dev.no.name.afriends.core;

import java.util.List;

/**
 * Опрос, как его рисует пузырь сообщения: варианты, голоса за каждый и свой выбор (-1 — ещё не голосовал).
 * Пока не голосовал, итоги не показываются, как в Telegram. Так устроены опросы и в объявлениях, и в группах
 */
public interface PollView {
    List<String> optionTexts();

    int votes(int option);

    int total();

    int mine();

    default boolean voted() {
        return mine() >= 0;
    }

    default int share(int option) {
        int total = total();
        return total == 0 ? 0 : Math.round(votes(option) * 100f / total);
    }
}
