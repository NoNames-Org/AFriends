package dev.no.name.afriends.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

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
        if (!message.images().isEmpty()) {
            JsonArray images = new JsonArray();
            message.images().forEach(image -> images.add(image.toJson()));
            item.add("imgs", images);
        }
        if (message.reaction() != null) {
            item.addProperty("rm", message.reaction());
        }
        if (message.partnerReaction() != null) {
            item.addProperty("rt", message.partnerReaction());
        }
        if (message.forwardedFrom() != null) {
            item.addProperty("fwd", message.forwardedFrom());
        }
        if (message.reply() != null) {
            item.add("re", message.reply().toJson());
        }
        if (message.author() != null) {
            item.addProperty("by", message.author());
        }
        if (message.poll() != null) {
            item.add("poll", message.poll().toJson());
        }
        return item;
    }

    public static PrivateMessage read(JsonObject item) {
        String id = item.has("id") ? item.get("id").getAsString() : null;
        List<MessageImage> images = new ArrayList<>();
        if (item.has("imgs") && item.get("imgs").isJsonArray()) {
            for (JsonElement element : item.getAsJsonArray("imgs")) {
                MessageImage image = element.isJsonObject() ? MessageImage.fromJson(element.getAsJsonObject()) : null;
                if (image != null) {
                    images.add(image);
                }
            }
        } else if (item.has("img") && item.get("img").isJsonObject()) {
            // так картинка хранилась в первой версии
            MessageImage image = MessageImage.fromJson(item.getAsJsonObject("img"));
            if (image != null) {
                images.add(image);
            }
        }
        String mine = item.has("rm") ? item.get("rm").getAsString() : null;
        String theirs = item.has("rt") ? item.get("rt").getAsString() : null;
        // в старых отложенных поля «me» нет: там всегда входящие
        return new PrivateMessage(item.has("me") && item.get("me").getAsBoolean(), item.get("text").getAsString(),
                item.get("time").getAsLong(), id, images, Reactions.valid(mine) ? mine : null, Reactions.valid(theirs) ? theirs : null,
                item.has("fwd") ? item.get("fwd").getAsString() : null,
                item.has("re") && item.get("re").isJsonObject() ? MessageReply.fromJson(item.getAsJsonObject("re")) : null,
                item.has("by") ? item.get("by").getAsString() : null,
                item.has("poll") && item.get("poll").isJsonObject() ? GroupPoll.fromJson(item.getAsJsonObject("poll")) : null);
    }
}
