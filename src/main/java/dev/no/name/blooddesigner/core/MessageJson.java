package dev.no.name.blooddesigner.core;

import com.google.gson.JsonObject;

/** Сообщение в файле переписки и в отложенных: старые поля как были, новые — только если есть. */
public final class MessageJson {
    private MessageJson() {
    }

    public static JsonObject write(PrivateMessage message) {
        JsonObject item = new JsonObject();
        item.addProperty("me", message.fromMe());
        item.addProperty("text", message.text());
        item.addProperty("time", message.time());
        if (message.id() != null) {
            item.addProperty("id", message.id());
        }
        if (message.image() != null) {
            item.add("img", message.image().toJson());
        }
        if (message.reaction() != null) {
            item.addProperty("rm", message.reaction());
        }
        if (message.partnerReaction() != null) {
            item.addProperty("rt", message.partnerReaction());
        }
        return item;
    }

    public static PrivateMessage read(JsonObject item) {
        String id = item.has("id") ? item.get("id").getAsString() : null;
        MessageImage image = item.has("img") && item.get("img").isJsonObject() ? MessageImage.fromJson(item.getAsJsonObject("img")) : null;
        String mine = item.has("rm") ? item.get("rm").getAsString() : null;
        String theirs = item.has("rt") ? item.get("rt").getAsString() : null;
        // в старых отложенных поля «me» нет: там всегда входящие
        return new PrivateMessage(item.has("me") && item.get("me").getAsBoolean(), item.get("text").getAsString(),
                item.get("time").getAsLong(), id, image, Reactions.valid(mine) ? mine : null, Reactions.valid(theirs) ? theirs : null);
    }
}
