package dev.no.name.afriends.net.presence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.no.name.afriends.net.model.Presence;

// статус в JSON до шифрования и после расшифровки; версия в поле v, чтобы формат можно было менять
final class PresenceCodec {
    private static final int VERSION = 2;

    private PresenceCodec() {
    }

    static String encode(Presence presence) {
        JsonObject json = new JsonObject();
        json.addProperty("v", VERSION);
        json.addProperty("where", presence.where().name());
        json.addProperty("server", presence.server());
        json.addProperty("status", presence.status());
        json.addProperty("since", presence.since());
        return json.toString();
    }

    // null, если формат чужой или поля битые
    static Presence decode(String text) {
        try {
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            if (json.get("v").getAsInt() != VERSION) {
                return null;
            }
            return new Presence(Presence.Where.valueOf(json.get("where").getAsString()), json.get("server").getAsString(),
                    json.get("status").getAsString(), json.get("since").getAsLong());
        } catch (RuntimeException e) {
            return null;
        }
    }
}
