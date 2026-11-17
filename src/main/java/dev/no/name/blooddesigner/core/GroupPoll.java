package dev.no.name.blooddesigner.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Опрос в групповом чате. Вопрос — текст сообщения. votes — кто из участников за какой вариант (ник в нижнем
 * регистре → номер варианта), mine — свой выбор, -1 — не голосовал. Голоса ходят сквозными конвертами
 * между участниками, сервер опрос не видит, поэтому у каждого свой подсчёт по тем голосам, что до него дошли
 */
public record GroupPoll(List<String> options, Map<String, Integer> votes, int mine) implements PollView {
    public static final int MIN_OPTIONS = 2;
    public static final int MAX_OPTIONS = 10;
    // вопрос и варианты вместе с запасным текстом для старых версий укладываются в 1000 знаков сообщения
    public static final int MAX_OPTION = 60;
    public static final int MAX_QUESTION = 200;

    public GroupPoll {
        options = List.copyOf(options);
        votes = Map.copyOf(votes);
    }

    public static GroupPoll fresh(List<String> options) {
        return new GroupPoll(options, Map.of(), -1);
    }

    /**
     * Варианты, годные для опроса: без управляющих символов и §, не пустые, не длиннее 60, без повторов,
     * от 2 до 10. null — так опрос не сделать
     */
    public static List<String> cleanOptions(List<String> raw) {
        List<String> clean = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String value : raw) {
            String option = value == null ? "" : value.replaceAll("[\\p{Cntrl}§]", "").replaceAll("\\s+", " ").strip();
            if (option.isEmpty() || option.length() > MAX_OPTION || !seen.add(option.toLowerCase(Locale.ROOT))) {
                return null;
            }
            clean.add(option);
        }
        return clean.size() < MIN_OPTIONS || clean.size() > MAX_OPTIONS ? null : clean;
    }

    @Override
    public List<String> optionTexts() {
        return options;
    }

    @Override
    public int votes(int option) {
        int count = mine == option ? 1 : 0;
        for (int choice : votes.values()) {
            if (choice == option) {
                count++;
            }
        }
        return count;
    }

    @Override
    public int total() {
        return votes.size() + (mine >= 0 ? 1 : 0);
    }

    // голос участника; choice -1 — отозвал. Номер вне вариантов (битый конверт) не принимается
    public GroupPoll withVote(String voter, int choice) {
        if (choice >= options.size()) {
            return this;
        }
        Map<String, Integer> next = new HashMap<>(votes);
        String key = voter.toLowerCase(Locale.ROOT);
        if (choice < 0) {
            next.remove(key);
        } else {
            next.put(key, choice);
        }
        return new GroupPoll(options, next, mine);
    }

    public GroupPoll withMine(int choice) {
        return new GroupPoll(options, votes, choice < options.size() ? choice : -1);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        JsonArray list = new JsonArray();
        options.forEach(list::add);
        json.add("o", list);
        JsonObject others = new JsonObject();
        votes.forEach(others::addProperty);
        json.add("v", others);
        json.addProperty("m", mine);
        return json;
    }

    // null — битая запись
    public static GroupPoll fromJson(JsonObject json) {
        try {
            List<String> raw = new ArrayList<>();
            for (JsonElement element : json.getAsJsonArray("o")) {
                raw.add(element.getAsString());
            }
            List<String> options = cleanOptions(raw);
            if (options == null) {
                return null;
            }
            Map<String, Integer> votes = new HashMap<>();
            if (json.has("v") && json.get("v").isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("v").entrySet()) {
                    int choice = entry.getValue().getAsInt();
                    if (choice >= 0 && choice < options.size()) {
                        votes.put(entry.getKey().toLowerCase(Locale.ROOT), choice);
                    }
                }
            }
            int mine = json.has("m") ? json.get("m").getAsInt() : -1;
            return new GroupPoll(options, votes, mine >= 0 && mine < options.size() ? mine : -1);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
