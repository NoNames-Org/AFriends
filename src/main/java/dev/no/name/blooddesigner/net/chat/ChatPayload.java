package dev.no.name.blooddesigner.net.chat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.no.name.blooddesigner.core.MessageImage;
import dev.no.name.blooddesigner.core.Reactions;
import dev.no.name.blooddesigner.net.crypto.Bytes;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

// Что лежит внутри сквозного конверта (CRYPTO.md, разделы 7 и 7а). Сервер видит только шифротекст,
// поэтому текст, картинки, реакции и ключи статуса для него неотличимы
public sealed interface ChatPayload {
    int MAX_TEXT = 1000;
    Pattern MESSAGE_ID = Pattern.compile("[0-9a-f]{16}");
    // старая версия мода покажет картинку этим текстом: поле img она не знает
    String IMAGE_FALLBACK = "[картинка — обнови BloodDesigner, чтобы увидеть]";

    // сообщение в ЛС; метки SMPHelper — тоже текст, их узнаёт карточка дополнения.
    // id — по нему ставятся реакции; image — картинка или гифка вместо текста
    record Text(String text, String id, MessageImage image) implements ChatPayload {
        public Text(String text) {
            this(text, null, null);
        }
    }

    // реакция на сообщение с этим id; reaction == null — снять свою
    record Reaction(String target, String reaction) implements ChatPayload {
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
                json.addProperty("t", text.image() != null && text.text().isBlank() ? IMAGE_FALLBACK : text.text());
                if (text.id() != null) {
                    json.addProperty("id", text.id());
                }
                if (text.image() != null) {
                    json.add("img", text.image().toJson());
                }
            }
            case Reaction reaction -> {
                json.addProperty("k", "react");
                json.addProperty("to", reaction.target());
                if (reaction.reaction() != null) {
                    json.addProperty("r", reaction.reaction());
                }
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
                case "text" -> text(json);
                case "react" -> {
                    String target = json.get("to").getAsString();
                    String reaction = json.has("r") ? json.get("r").getAsString() : null;
                    yield MESSAGE_ID.matcher(target).matches() && (reaction == null || Reactions.valid(reaction))
                            ? new Reaction(target, reaction) : null;
                }
                case "pkey" -> new PresenceKey(json.get("e").getAsLong(), Bytes.fromBase64(json.get("key").getAsString()));
                default -> new Unknown(kind);
            };
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Text text(JsonObject json) {
        String text = json.get("t").getAsString();
        String id = json.has("id") ? json.get("id").getAsString() : null;
        if (id != null && !MESSAGE_ID.matcher(id).matches()) {
            id = null;
        }
        MessageImage image = json.has("img") && json.get("img").isJsonObject() ? MessageImage.fromJson(json.getAsJsonObject("img")) : null;
        if (image != null) {
            // запасной текст для старых версий новой версии не нужен: вместо него картинка
            return new Text(IMAGE_FALLBACK.equals(text) ? "" : text, id, image);
        }
        return text.isBlank() || text.length() > MAX_TEXT ? null : new Text(text, id, null);
    }
}
