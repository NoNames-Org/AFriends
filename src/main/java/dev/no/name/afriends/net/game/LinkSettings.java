package dev.no.name.afriends.net.game;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.no.name.afriends.net.model.Presence;

// Настройки сети: подключаться ли вообще, показывать ли друзьям сервер, виден ли значок чужим, свой статус.
// Хранятся в общем конфиге AFriends
public final class LinkSettings {
    private boolean enabled = true;
    private boolean shareServer = true;
    private boolean visible = true;
    private String status = "";
    private Runnable onChange = () -> { };

    public void setOnChange(Runnable listener) {
        onChange = listener;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
        onChange.run();
    }

    public boolean shareServer() {
        return shareServer;
    }

    public void setShareServer(boolean value) {
        shareServer = value;
        onChange.run();
    }

    public boolean visible() {
        return visible;
    }

    public void setVisible(boolean value) {
        visible = value;
        onChange.run();
    }

    public String status() {
        return status;
    }

    public void setStatus(String value) {
        status = Presence.cleanStatus(value);
        onChange.run();
    }

    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("enabled", enabled);
        json.addProperty("shareServer", shareServer);
        json.addProperty("visible", visible);
        json.addProperty("status", status);
        return json;
    }

    public void fromJson(JsonElement json) {
        if (!json.isJsonObject()) {
            return;
        }
        JsonObject object = json.getAsJsonObject();
        enabled = !object.has("enabled") || object.get("enabled").getAsBoolean();
        shareServer = !object.has("shareServer") || object.get("shareServer").getAsBoolean();
        visible = !object.has("visible") || object.get("visible").getAsBoolean();
        status = object.has("status") ? object.get("status").getAsString() : "";
    }
}
