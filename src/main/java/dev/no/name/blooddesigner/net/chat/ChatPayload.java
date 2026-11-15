package dev.no.name.blooddesigner.net.chat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.no.name.blooddesigner.net.crypto.Bytes;

import java.nio.charset.StandardCharsets;

// Что лежит внутри сквозного конверта (CRYPTO.md, разделы 7 и 7а). Сервер видит только шифротекст,
// поэтому текст, метки и ключи статуса для него неотличимы
public sealed interface ChatPayload {
    int MAX_TEXT = 1000;

    // сообщение в ЛС; метки SMPHelper — тоже текст, их узнаёт карточка дополнения
    record Text(String text) implements ChatPayload {
    }

    // ключ, которым друг шифрует свой статус
    record PresenceKey(long epoch, byte[] key) implements ChatPayload {
    }

    // вид из будущей версии: молча пропускаем
    record Unknown(String kind) implements ChatPayload {
    }

    default byte[] encode() {
        JsonObject json = new JsonObject();
        switch (this) {
            case Text text -> {
                json.addProperty("k", "text");
                json.addProperty("t", text.text());
            }
            case PresenceKey key -> {
                json.addProperty("k", "pkey");
                json.addProperty("e", key.epoch());
                json.addProperty("key", Bytes.base64(key.key()));
            }
            case Unknown unknown -> throw new IllegalStateException("неизвестное не отправляется");
        }
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    // null — не JSON или битые поля
    static ChatPayload decode(byte[] data) {
        try {
            JsonObject json = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
            String kind = json.get("k").getAsString();
            return switch (kind) {
                case "text" -> {
                    String text = json.get("t").getAsString();
                    yield text.isBlank() || text.length() > MAX_TEXT ? null : new Text(text);
                }
                case "pkey" -> new PresenceKey(json.get("e").getAsLong(), Bytes.fromBase64(json.get("key").getAsString()));
                default -> new Unknown(kind);
            };
        } catch (RuntimeException e) {
            return null;
        }
    }
}
