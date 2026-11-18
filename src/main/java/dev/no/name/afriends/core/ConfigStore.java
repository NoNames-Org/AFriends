package dev.no.name.afriends.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.no.name.afriends.AFriends;
import dev.no.name.afriends.gui.theme.ThemeSettings;
import dev.no.name.afriends.net.game.LinkSettings;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ConfigStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(AFriends.NAME);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FRIENDS = "friends";
    private static final String IGNORED = "ignored";
    private static final String THEME = "theme";
    private static final String MESSAGE_LIMIT = "messageLimit";
    private static final String NETWORK = "network";
    private static final String NOTIFY = "notify";
    private static final String SOUND = "sound";

    private final SocialData social;
    private final ThemeSettings theme;
    private final LinkSettings network;
    private final NotifySettings notify;
    private final SoundSettings sound;
    private final Path file;

    public ConfigStore(SocialData social, ThemeSettings theme, LinkSettings network,
                       NotifySettings notify, SoundSettings sound) {
        this.notify = notify;
        this.sound = sound;
        this.social = social;
        this.network = network;
        this.theme = theme;
        this.file = FabricLoader.getInstance().getConfigDir().resolve(AFriends.MOD_ID).resolve("config.json");
    }

    public void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            JsonElement root = JsonParser.parseString(Files.readString(file));
            if (!root.isJsonObject()) {
                LOGGER.warn("Конфиг {} не объект JSON — пропускаю", file);
                return;
            }
            JsonObject json = root.getAsJsonObject();
            if (json.has(FRIENDS)) {
                social.friendsFromJson(json.get(FRIENDS));
            }
            if (json.has(IGNORED)) {
                social.ignoredFromJson(json.get(IGNORED));
            }
            if (json.has(THEME)) {
                theme.fromJson(json.get(THEME));
            }
            if (json.has(SOUND)) {
                sound.fromJson(json.get(SOUND));
            }
            if (json.has(NOTIFY)) {
                notify.fromJson(json.get(NOTIFY));
            }
            if (json.has(NETWORK)) {
                network.fromJson(json.get(NETWORK));
            }
            if (json.has(MESSAGE_LIMIT) && json.get(MESSAGE_LIMIT).isJsonPrimitive()) {
                MessageHistory.set(json.get(MESSAGE_LIMIT).getAsInt());
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Не удалось прочитать конфиг {}", file, e);
        }
    }

    public void save() {
        JsonObject json = new JsonObject();
        json.add(FRIENDS, social.friendsJson());
        json.add(IGNORED, social.ignoredJson());
        json.add(THEME, theme.toJson());
        json.addProperty(MESSAGE_LIMIT, MessageHistory.limit());
        json.add(NETWORK, network.toJson());
        json.add(NOTIFY, notify.toJson());
        json.add(SOUND, sound.toJson());
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(json));
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.error("Не удалось сохранить конфиг в {}", file, e);
        }
    }
}
