package dev.no.name.afriends.core;

import com.google.gson.JsonObject;

/**
 * Ответ на сообщение, как в Telegram: ключ исходного (по нему прокручиваемся к нему), чьё оно и начало текста.
 * Начало текста едет вместе с ответом: у собеседника исходного сообщения может уже не быть (история обрезана, удалено).
 * По сети ключ — id сообщения; у заметок и /msg — ключ у себя (см. PrivateMessage.key)
 */
public record MessageReply(String id, String author, String snippet) {
    public static final int MAX_SNIPPET = 80;
    public static final int MAX_AUTHOR = 32;
    private static final int MAX_ID = 40;

    public MessageReply {
        author = clean(author, MAX_AUTHOR);
        snippet = clean(snippet, MAX_SNIPPET);
    }

    // ответ на это сообщение: у картинки без подписи вместо текста — «Изображение» или «Гифка»
    public static MessageReply to(PrivateMessage message, String author) {
        return new MessageReply(message.key(), author, preview(message));
    }

    // короткий текст сообщения для цитаты и плашки закрепа
    public static String preview(PrivateMessage message) {
        String text = MessageContent.plain(message.text()).strip();
        if (text.isEmpty() && !message.images().isEmpty()) {
            text = message.image().voice() ? "Голосовое сообщение" : message.image().animated() ? "Гифка" : "Изображение";
        }
        return clean(text, MAX_SNIPPET);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("a", author);
        json.addProperty("s", snippet);
        return json;
    }

    // null — битое
    public static MessageReply fromJson(JsonObject json) {
        try {
            String id = json.get("id").getAsString();
            if (id.isEmpty() || id.length() > MAX_ID) {
                return null;
            }
            return new MessageReply(id, json.has("a") ? json.get("a").getAsString() : "",
                    json.has("s") ? json.get("s").getAsString() : "");
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String clean(String value, int max) {
        if (value == null) {
            return "";
        }
        String flat = value.replaceAll("[\\p{Cntrl}§]", " ").replaceAll(" {2,}", " ").strip();
        return flat.length() > max ? flat.substring(0, max - 1) + "…" : flat;
    }
}
