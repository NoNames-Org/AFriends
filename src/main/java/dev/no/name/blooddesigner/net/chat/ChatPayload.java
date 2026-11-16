package dev.no.name.blooddesigner.net.chat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import dev.no.name.blooddesigner.core.MessageImage;
import dev.no.name.blooddesigner.core.MessageReply;
import dev.no.name.blooddesigner.core.Reactions;
import dev.no.name.blooddesigner.net.crypto.Bytes;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

// Что лежит внутри сквозного конверта (CRYPTO.md, разделы 7 и 7а). Сервер видит только шифротекст,
// поэтому текст, картинки, реакции и ключи статуса для него неотличимы
public sealed interface ChatPayload {
    int MAX_TEXT = 1000;
    Pattern MESSAGE_ID = Pattern.compile("[0-9a-f]{16}");
    // старая версия мода покажет картинку этим текстом: поле img она не знает
    String IMAGE_FALLBACK = "[картинка — обнови BloodDesigner, чтобы увидеть]";
    String VOICE_FALLBACK = "[голосовое — обнови BloodDesigner, чтобы послушать]";

    // сообщение в ЛС; метки SMPHelper — тоже текст, их узнаёт карточка дополнения.
    // id — по нему ставятся реакции; image — картинка или гифка; forwardedFrom — чьё сообщение переслали;
    // reply — на какое сообщение ответ (старые версии его не знают и покажут просто текст)
    // group — id группового чата (null — личка). Старые версии поле g не знают и покажут сообщение в личке отправителя
    record Text(String text, String id, MessageImage image, String forwardedFrom, MessageReply reply, String group) implements ChatPayload {
        public Text(String text, String id, MessageImage image, String forwardedFrom, MessageReply reply) {
            this(text, id, image, forwardedFrom, reply, null);
        }

        public Text(String text) {
            this(text, null, null, null, null);
        }

        public Text(String text, String id, MessageImage image) {
            this(text, id, image, null, null);
        }

        public Text(String text, String id, MessageImage image, String forwardedFrom) {
            this(text, id, image, forwardedFrom, null);
        }
    }

    int MAX_DELETE = 100;

    // удалить у обоих сообщения с этими id (свои и собеседника, как в личке Telegram)
    record Delete(List<String> targets) implements ChatPayload {
        public Delete {
            targets = List.copyOf(targets);
        }
    }

    // закрепить (pinned) или открепить у обоих сообщение с этим id
    record Pin(String target, boolean pinned) implements ChatPayload {
    }

    // реакция на сообщение с этим id; reaction == null — снять свою
    record Reaction(String target, String reaction) implements ChatPayload {
    }

    Pattern GROUP_ID = Pattern.compile("[0-9a-f]{16}");
    int MAX_GROUP_NAME = 32;

    // название группы: рассылает тот, кто создал или переименовал. Сервер его не видит
    record GroupName(String group, String name) implements ChatPayload {
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
                json.addProperty("t", text.image() == null ? text.text()
                        : text.text().isBlank() ? fallback(text.image()) : text.text() + "\n" + fallback(text.image()));
                if (text.id() != null) {
                    json.addProperty("id", text.id());
                }
                if (text.image() != null) {
                    json.add("img", text.image().toJson());
                }
                if (text.forwardedFrom() != null) {
                    json.addProperty("f", text.forwardedFrom());
                }
                if (text.reply() != null) {
                    json.add("re", text.reply().toJson());
                }
                if (text.group() != null) {
                    json.addProperty("g", text.group());
                }
            }
            case Delete delete -> {
                json.addProperty("k", "del");
                JsonArray targets = new JsonArray();
                delete.targets().forEach(targets::add);
                json.add("to", targets);
            }
            case Pin pin -> {
                json.addProperty("k", "pin");
                json.addProperty("to", pin.target());
                json.addProperty("on", pin.pinned());
            }
            case Reaction reaction -> {
                json.addProperty("k", "react");
                json.addProperty("to", reaction.target());
                if (reaction.reaction() != null) {
                    json.addProperty("r", reaction.reaction());
                }
            }
            case GroupName group -> {
                json.addProperty("k", "gname");
                json.addProperty("g", group.group());
                json.addProperty("n", group.name());
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
                case "del" -> delete(json);
                case "pin" -> {
                    String target = json.get("to").getAsString();
                    yield MESSAGE_ID.matcher(target).matches() ? new Pin(target, json.get("on").getAsBoolean()) : null;
                }
                case "gname" -> {
                    String group = json.get("g").getAsString();
                    String name = groupName(json.get("n").getAsString());
                    yield GROUP_ID.matcher(group).matches() && name != null ? new GroupName(group, name) : null;
                }
                case "pkey" -> new PresenceKey(json.get("e").getAsLong(), Bytes.fromBase64(json.get("key").getAsString()));
                default -> new Unknown(kind);
            };
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Delete delete(JsonObject json) {
        List<String> targets = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("to")) {
            String target = element.getAsString();
            if (!MESSAGE_ID.matcher(target).matches()) {
                return null;
            }
            targets.add(target);
        }
        return targets.isEmpty() || targets.size() > MAX_DELETE ? null : new Delete(targets);
    }

    private static Text text(JsonObject json) {
        String text = json.get("t").getAsString();
        String id = json.has("id") ? json.get("id").getAsString() : null;
        if (id != null && !MESSAGE_ID.matcher(id).matches()) {
            id = null;
        }
        MessageImage image = json.has("img") && json.get("img").isJsonObject() ? MessageImage.fromJson(json.getAsJsonObject("img")) : null;
        String forwarded = json.has("f") ? forwardedName(json.get("f").getAsString()) : null;
        MessageReply reply = json.has("re") && json.get("re").isJsonObject() ? MessageReply.fromJson(json.getAsJsonObject("re")) : null;
        if (reply != null && !MESSAGE_ID.matcher(reply.id()).matches()) {
            reply = null;
        }
        String group = json.has("g") ? json.get("g").getAsString() : null;
        if (group != null && !GROUP_ID.matcher(group).matches()) {
            return null;
        }
        if (image != null) {
            // запасной текст для старых версий новой версии не нужен: вместо него картинка, остаётся подпись
            String fallback = fallback(image);
            String caption = text.endsWith(fallback) ? text.substring(0, text.length() - fallback.length()).strip() : text;
            return new Text(caption.length() > MAX_TEXT ? caption.substring(0, MAX_TEXT) : caption, id, image, forwarded, reply, group);
        }
        return text.isBlank() || text.length() > MAX_TEXT ? null : new Text(text, id, null, forwarded, reply, group);
    }

    // что покажет старая версия вместо вложения
    static String fallback(MessageImage image) {
        return image.voice() ? VOICE_FALLBACK : IMAGE_FALLBACK;
    }

    // название группы: без управляющих символов и §, не пустое и не длиннее 32
    static String groupName(String value) {
        String clean = value.replaceAll("[\\p{Cntrl}§]", "").replaceAll("\\s+", " ").strip();
        return clean.isEmpty() ? null : clean.length() > MAX_GROUP_NAME ? clean.substring(0, MAX_GROUP_NAME) : clean;
    }

    // подпись «Переслано от …»: ник или название чата, без переводов строк и не длиннее 32
    private static String forwardedName(String value) {
        String clean = value.replaceAll("[\\p{Cntrl}§]", "").strip();
        return clean.isEmpty() ? null : clean.length() > 32 ? clean.substring(0, 32) : clean;
    }
}
