package dev.no.name.blooddesigner.core;

import com.google.gson.JsonObject;

import java.util.regex.Pattern;

/**
 * Картинка или гифка в сообщении. Сам файл лежит на сервере сети зашифрованным (blob),
 * ключ знают только собеседники. sha256 — от исходного файла: по нему файл ищется в кеше и проверяется после расшифровки.
 */
public record MessageImage(String blob, String key, String mime, int width, int height, long size, String sha256) {
    public static final long MAX_SIZE = 5L * 1024 * 1024;
    private static final Pattern BLOB = Pattern.compile("[0-9a-f]{32}");
    private static final Pattern SHA = Pattern.compile("[0-9a-f]{64}");

    public boolean animated() {
        return "image/gif".equals(mime);
    }

    public String extension() {
        return switch (mime) {
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
                && (mime.equals("image/png") || mime.equals("image/jpeg") || mime.equals("image/gif"))
                && width > 0 && height > 0 && width <= 16384 && height <= 16384 && size > 0 && size <= MAX_SIZE;
    }
}
