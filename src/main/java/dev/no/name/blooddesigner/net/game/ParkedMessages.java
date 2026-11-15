package dev.no.name.blooddesigner.net.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.no.name.blooddesigner.BloodDesigner;
import dev.no.name.blooddesigner.core.PrivateMessage;
import dev.no.name.blooddesigner.net.identity.SecretFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// Сообщения, пришедшие, пока игрок в меню. Переписки в BloodDesigner живут по серверам, а в меню
// сервера нет: держим тут (и на диске — конверт на сервере уже подтверждён) до входа в мир
final class ParkedMessages {
    private static final Logger LOGGER = LoggerFactory.getLogger(BloodDesigner.NAME);

    record Parked(String partner, PrivateMessage message) {
    }

    private final Path file;
    private final List<Parked> parked = new ArrayList<>();

    ParkedMessages(Path file) {
        this.file = file;
        load();
    }

    void add(String partner, PrivateMessage message) {
        parked.add(new Parked(partner, message));
        save();
    }

    boolean isEmpty() {
        return parked.isEmpty();
    }

    List<Parked> takeAll() {
        List<Parked> all = List.copyOf(parked);
        parked.clear();
        save();
        return all;
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            for (JsonElement element : JsonParser.parseString(Files.readString(file)).getAsJsonArray()) {
                JsonObject item = element.getAsJsonObject();
                parked.add(new Parked(item.get("partner").getAsString(), new PrivateMessage(false,
                        item.get("text").getAsString(), item.get("time").getAsLong())));
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("{}: отложенные сообщения не прочитались", BloodDesigner.NAME, e);
        }
    }

    private void save() {
        JsonArray array = new JsonArray();
        for (Parked item : parked) {
            JsonObject json = new JsonObject();
            json.addProperty("partner", item.partner());
            json.addProperty("text", item.message().text());
            json.addProperty("time", item.message().time());
            array.add(json);
        }
        try {
            // текст переписки — только для владельца, как и ключи
            SecretFiles.write(file, array.toString());
        } catch (IOException e) {
            LOGGER.warn("{}: отложенные сообщения не сохранились", BloodDesigner.NAME, e);
        }
    }
}
