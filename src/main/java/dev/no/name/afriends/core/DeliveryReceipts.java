package dev.no.name.afriends.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.no.name.afriends.AFriends;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/** Отметки «доставлено» и «прочитано» на свои сообщения по id; хранятся последние MAX_RECEIPTS. */
final class DeliveryReceipts {
    static final String JSON_KEY = "$receipts";
    static final int NONE = 0;
    static final int DELIVERED = 1;
    static final int READ = 2;
    private static final int MAX_RECEIPTS = 4000;
    private static final Logger LOGGER = LoggerFactory.getLogger(AFriends.NAME);

    // порядок вставки: при переполнении уходят самые старые
    private final Map<String, Integer> receipts = new LinkedHashMap<>();

    int state(String messageId) {
        return messageId == null ? NONE : receipts.getOrDefault(messageId, NONE);
    }

    /** Отметка только повышается: «прочитано» не превратится обратно в «доставлено». false — ничего не поменялось. */
    boolean mark(String messageId, int state) {
        if (messageId == null || state <= state(messageId)) {
            return false;
        }
        receipts.remove(messageId);
        receipts.put(messageId, state);
        while (receipts.size() > MAX_RECEIPTS) {
            receipts.remove(receipts.keySet().iterator().next());
        }
        return true;
    }

    void clear() {
        receipts.clear();
    }

    void writeTo(JsonObject json) {
        JsonObject receiptsJson = new JsonObject();
        receipts.forEach(receiptsJson::addProperty);
        json.add(JSON_KEY, receiptsJson);
    }

    void read(JsonElement json) {
        if (!json.isJsonObject()) {
            return;
        }
        json.getAsJsonObject().entrySet().forEach(entry -> {
            try {
                receipts.put(entry.getKey(), entry.getValue().getAsInt());
            } catch (RuntimeException e) {
                LOGGER.warn("Пропущена битая запись {} в {}", entry.getKey(), JSON_KEY);
            }
        });
    }
}
