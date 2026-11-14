package dev.no.name.blooddesigner.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.no.name.blooddesigner.BloodDesigner;
import dev.no.name.blooddesigner.core.modes.ModeFavorites;
import dev.no.name.blooddesigner.gui.theme.ThemeSettings;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ConfigStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(BloodDesigner.NAME);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FRIENDS = "friends";
    private static final String IGNORED = "ignored";
    private static final String FAVORITE_MODES = "favoriteModes";
    private static final String THEME = "theme";
    private static final String MESSAGE_LIMIT = "messageLimit";

    private final SocialData social;
    private final ModeFavorites favorites;
    private final ThemeSettings theme;
    private final Path file;

    public ConfigStore(SocialData social, ModeFavorites favorites, ThemeSettings theme) {
        this.social = social;
        this.favorites = favorites;
        this.theme = theme;
        this.file = FabricLoader.getInstance().getConfigDir().resolve(BloodDesigner.MOD_ID).resolve("config.json");
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
            if (json.has(FAVORITE_MODES)) {
                favorites.fromJson(json.get(FAVORITE_MODES));
            }
            if (json.has(THEME)) {
                theme.fromJson(json.get(THEME));
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
        json.add(FAVORITE_MODES, favorites.toJson());
        json.add(THEME, theme.toJson());
        json.addProperty(MESSAGE_LIMIT, MessageHistory.limit());
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
