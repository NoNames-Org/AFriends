package dev.no.name.blooddesigner.net.chat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.no.name.blooddesigner.BloodDesigner;
import dev.no.name.blooddesigner.net.rpc.Failures;
import dev.no.name.blooddesigner.net.rpc.LinkCalls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

// Переписка игрока с BloodDesigner: сообщения, которые админы пишут с портала.
// Сервер хранит их и шлёт событие «official», мод забирает всё, что новее последнего полученного.
// Номер последнего хранится в файле, чтобы после перезапуска игры сообщения не пришли второй раз.
// Живёт на потоке сети: туннель зовёт обработчики только оттуда
public final class OfficialInbox {
    private static final Logger LOGGER = LoggerFactory.getLogger(BloodDesigner.NAME);

    private final Path stateFile;
    private final InboxListener listener;
    private long lastId = -1;
    private boolean loading;
    private boolean again;

    public OfficialInbox(Path stateFile, InboxListener listener) {
        this.stateFile = stateFile;
        this.listener = listener;
    }

    // зовётся при входе в сеть и на событие сервера; если запрос уже идёт — повторится после него
    public void fetch(LinkCalls connection) {
        if (loading) {
            again = true;
            return;
        }
        loading = true;
        again = false;
        JsonObject data = new JsonObject();
        data.addProperty("after", lastId());
        connection.request("official.inbox", data).whenComplete((json, error) -> {
            loading = false;
            if (error != null) {
                LOGGER.debug("{}: сообщения BloodDesigner не загрузились ({})", BloodDesigner.NAME, Failures.codeOf(error));
            } else {
                deliver(json);
            }
            if (again) {
                fetch(connection);
            }
        });
    }

    private void deliver(JsonObject json) {
        if (json == null || !json.has("messages") || !json.get("messages").isJsonArray()) {
            return;
        }
        long newest = lastId();
        for (JsonElement element : json.getAsJsonArray("messages")) {
            JsonObject item = element.getAsJsonObject();
            long id = item.get("id").getAsLong();
            if (id <= newest) {
                continue;
            }
            newest = id;
            // свои ответы уже в переписке: мод кладёт их туда сам, как только сервер их принял
            if (item.has("fromPlayer") && item.get("fromPlayer").getAsBoolean()) {
                continue;
            }
            listener.official(item.get("text").getAsString(), item.get("created").getAsLong());
        }
        if (newest != lastId) {
            lastId = newest;
            save();
        }
    }

    private long lastId() {
        if (lastId < 0) {
            try {
                lastId = Files.exists(stateFile) ? Long.parseLong(Files.readString(stateFile, StandardCharsets.UTF_8).strip()) : 0;
            } catch (IOException | NumberFormatException e) {
                lastId = 0;
            }
        }
        return lastId;
    }

    private void save() {
        try {
            Files.writeString(stateFile, Long.toString(lastId), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("{}: не записался номер последнего сообщения BloodDesigner", BloodDesigner.NAME, e);
        }
    }
}
