package dev.no.name.afriends.net.service;

import com.google.gson.JsonObject;
import dev.no.name.afriends.core.MessageImage;
import dev.no.name.afriends.net.media.PreparedImage;

import java.util.concurrent.CompletableFuture;

/** Чат «AFriends»: ответы уходят админам на портал, поэтому картинки здесь не шифруются. */
public final class OfficialGateway {
    private final LinkCalls calls;

    OfficialGateway(LinkCalls calls) {
        this.calls = calls;
    }

    // «ok» или код причины
    public CompletableFuture<String> send(String text) {
        JsonObject data = new JsonObject();
        data.addProperty("text", text);
        return calls.request("official.send", data);
    }

    public CompletableFuture<MessageImage> sendImage(PreparedImage prepared, String caption) {
        return calls.sendOfficialImage(prepared, caption);
    }
}
