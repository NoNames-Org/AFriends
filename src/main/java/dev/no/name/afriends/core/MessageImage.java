package dev.no.name.afriends.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.no.name.afriends.core.voice.VoiceClip;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Картинка или гифка в сообщении. От друга: файл лежит на сервере сети зашифрованным (blob), ключ знают только
 * собеседники. С портала (объявления, AFriends): файл открытый, ключа нет (key пустой), blob — id картинки портала.
 * sha256 — от исходного файла: по нему файл ищется в кеше и проверяется после скачивания.
 */
public record MessageImage(String blob, String key, String mime, int width, int height, long size, String sha256) {
    public static final long MAX_SIZE = 5L * 1024 * 1024;
    private static final Pattern BLOB = Pattern.compile("[0-9a-f]{32}");
    private static final Pattern SHA = Pattern.compile("[0-9a-f]{64}");

    // картинка с портала: не зашифрована, качается с /v1/media
    public boolean isPublic() {
        return key.isEmpty();
    }

    // голосовое сообщение (core.voice.VoiceClip): width — длительность в сотых секунды
    public boolean voice() {
        return VoiceClip.MIME.equals(mime);
    }

    public int voiceCentiseconds() {
        return voice() ? width : 0;
    }

    public boolean animated() {
        return "image/gif".equals(mime);
    }

    public String extension() {
        return switch (mime) {
            case VoiceClip.MIME -> "bdv";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            default -> "jpg";
        };
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("b", blob);
        json.addProperty("k", key);
        json.addProperty("m", mime);
        json.addProperty("w", width);
        json.addProperty("h", height);
        json.addProperty("s", size);
        json.addProperty("sha", sha256);
        return json;
    }

    // картинка портала в ответе сервера: {id, mime, width, height, size, sha256}; ключа нет — она открытая
    public static MessageImage fromPortal(JsonObject json) {
        try {
            MessageImage image = new MessageImage(json.get("id").getAsString(), "", json.get("mime").getAsString(),
                    json.get("width").getAsInt(), json.get("height").getAsInt(), json.get("size").getAsLong(),
                    json.get("sha256").getAsString());
            return image.valid() ? image : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static List<MessageImage> listFromPortal(JsonObject owner) {
        List<MessageImage> images = new ArrayList<>();
        if (owner.has("images") && owner.get("images").isJsonArray()) {
            for (JsonElement element : owner.getAsJsonArray("images")) {
                MessageImage image = element.isJsonObject() ? fromPortal(element.getAsJsonObject()) : null;
                if (image != null) {
                    images.add(image);
                }
            }
        }
        return images;
    }

    // null — поля битые или подозрительные: такое сообщение покажется обычным текстом
    public static MessageImage fromJson(JsonObject json) {
        try {
            MessageImage image = new MessageImage(json.get("b").getAsString(), json.get("k").getAsString(),
                    json.get("m").getAsString(), json.get("w").getAsInt(), json.get("h").getAsInt(),
                    json.get("s").getAsLong(), json.get("sha").getAsString());
            return image.valid() ? image : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean valid() {
        return BLOB.matcher(blob).matches() && SHA.matcher(sha256).matches() && key.length() <= 64
                && (mime.equals("image/png") || mime.equals("image/jpeg") || mime.equals("image/gif") || voice())
                && width > 0 && height > 0 && width <= 16384 && height <= 16384 && size > 0 && size <= MAX_SIZE;
    }
}
