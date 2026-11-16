package dev.no.name.blooddesigner.net.announcements;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Голоса в опросах через туннель сети: poll.vote и poll.mine. Свой выбор помнится здесь, чтобы
 * отметить вариант сразу после голоса и после перезагрузки ленты. Без сети голосовать нельзя
 */
public final class PollVotes {
    private final Map<Long, Integer> mine = new ConcurrentHashMap<>();
    private volatile BiFunction<String, JsonObject, CompletableFuture<JsonObject>> requester;

    // ставит сеть при запуске; запрос возвращает null, если связи нет
    public void setRequester(BiFunction<String, JsonObject, CompletableFuture<JsonObject>> value) {
        requester = value;
    }

    public int mine(long announcement) {
        return mine.getOrDefault(announcement, -1);
    }

    // choice -1 — отозвать голос. done получает свежий опрос или null, если не вышло (на потоке сети)
    public void vote(AnnouncementPoll poll, int choice, Consumer<AnnouncementPoll> done) {
        BiFunction<String, JsonObject, CompletableFuture<JsonObject>> request = requester;
        if (request == null) {
            done.accept(null);
            return;
        }
        JsonObject data = new JsonObject();
        data.addProperty("id", poll.announcement());
        data.addProperty("choice", choice);
        request.apply("poll.vote", data).whenComplete((json, error) -> {
            if (error != null || json == null) {
                done.accept(null);
                return;
            }
            mine.put(poll.announcement(), choice);
            AnnouncementPoll fresh = AnnouncementPoll.parse(poll.announcement(), json.get("poll"), choice);
            done.accept(fresh != null ? fresh : poll.withMine(choice));
        });
    }

    // за что голосовали раньше, в том числе с другого компьютера; then — когда ответ разложен
    void loadMine(Collection<Long> announcements, Runnable then) {
        BiFunction<String, JsonObject, CompletableFuture<JsonObject>> request = requester;
        if (request == null || announcements.isEmpty()) {
            return;
        }
        JsonObject data = new JsonObject();
        JsonArray ids = new JsonArray();
        announcements.forEach(ids::add);
        data.add("ids", ids);
        request.apply("poll.mine", data).whenComplete((json, error) -> {
            if (error != null || json == null || !json.has("choices") || !json.get("choices").isJsonObject()) {
                return;
            }
            announcements.forEach(id -> mine.remove(id));
            json.getAsJsonObject("choices").entrySet().forEach(entry -> {
                try {
                    mine.put(Long.parseLong(entry.getKey()), entry.getValue().getAsInt());
                } catch (RuntimeException ignored) {
                    // чужое поле — пропускаем
                }
            });
            then.run();
        });
    }
}
