package dev.no.name.afriends.net.announcements;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.no.name.afriends.core.PollView;

import java.util.ArrayList;
import java.util.List;

/**
 * Опрос в объявлении: вопрос — текст объявления, варианты с числом голосов. mine — свой выбор,
 * -1 — ещё не голосовал. Пока не голосовал, проценты не показываются, как в Telegram
 */
public record AnnouncementPoll(long announcement, List<Option> options, int total, int mine) implements PollView {
    public static final int MAX_OPTIONS = 10;
    private static final int MAX_OPTION = 80;

    public record Option(String text, int votes) {
    }

    public AnnouncementPoll {
        options = List.copyOf(options);
    }

    @Override
    public List<String> optionTexts() {
        return options.stream().map(Option::text).toList();
    }

    @Override
    public int votes(int option) {
        return options.get(option).votes();
    }

    public AnnouncementPoll withMine(int choice) {
        return new AnnouncementPoll(announcement, options, total, choice);
    }

    // {options: [{text, votes}], total} из ответа сервера; null — не опрос или битые поля
    public static AnnouncementPoll parse(long announcement, JsonElement json, int mine) {
        if (json == null || !json.isJsonObject()) {
            return null;
        }
        try {
            JsonObject object = json.getAsJsonObject();
            List<Option> options = new ArrayList<>();
            int total = 0;
            for (JsonElement element : object.getAsJsonArray("options")) {
                JsonObject option = element.getAsJsonObject();
                String text = option.get("text").getAsString().replaceAll("[\\p{Cntrl}§]", "").strip();
                int votes = Math.max(0, option.get("votes").getAsInt());
                options.add(new Option(text.length() > MAX_OPTION ? text.substring(0, MAX_OPTION) : text, votes));
                total += votes;
            }
            if (options.size() < 2 || options.size() > MAX_OPTIONS) {
                return null;
            }
            return new AnnouncementPoll(announcement, options, total, mine >= options.size() ? -1 : mine);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
