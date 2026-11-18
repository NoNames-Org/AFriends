package dev.no.name.afriends.core;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import dev.no.name.afriends.AFriends;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class HeadCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(AFriends.NAME);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String TEXTURES = "textures";

    private static final int MAX_PROFILES = 512;
    private static final long SAVE_DELAY_MS = 5000;

    private static HeadCache instance = new HeadCache();

    public record Profile(UUID id, String name, String textures, String signature) {
    }

    private final Map<String, Profile> profiles = new LinkedHashMap<>();
    private final Map<String, PlayerSkin> loaded = new HashMap<>();
    private final Set<String> requested = new HashSet<>();
    private Path file;
    private boolean dirty;
    private long changedAt;

    public static HeadCache get() {
        return instance;
    }

    public static void init() {
        HeadCache cache = new HeadCache();
        cache.file = FabricLoader.getInstance().getConfigDir().resolve(AFriends.MOD_ID).resolve("heads.json");
        cache.load();
        instance = cache;
    }

    public void remember(PlayerInfo info) {
        GameProfile profile = info.getProfile();
        Property textures = profile.properties().get(TEXTURES).stream().findFirst().orElse(null);
        if (textures != null && remember(profile.name(), profile.id(), textures.value(), textures.signature())) {
            dirty = true;
            changedAt = System.currentTimeMillis();
        }
    }

    boolean remember(String name, UUID id, String textures, String signature) {
        if (name == null || id == null || textures == null || textures.isEmpty()) {
            return false;
        }
        Profile next = new Profile(id, name, textures, signature);
        Profile previous = profiles.remove(key(name));
        profiles.put(key(name), next);
        trim();
        if (previous != null && Objects.equals(previous.textures(), textures)) {
            return false;
        }
        loaded.remove(key(name));
        requested.remove(key(name));
        return true;
    }

    Profile profile(String name) {
        return profiles.get(key(name));
    }

    public PlayerSkin skin(String name) {
        String key = key(name);
        PlayerSkin skin = loaded.get(key);
        if (skin != null) {
            return skin;
        }
        Profile profile = profiles.get(key);
        if (profile != null && requested.add(key)) {
            request(key, profile);
        }
        return null;
    }

    private void request(String key, Profile profile) {
        Multimap<String, Property> properties = LinkedHashMultimap.create();
        properties.put(TEXTURES, profile.signature() == null
                ? new Property(TEXTURES, profile.textures())
                : new Property(TEXTURES, profile.textures(), profile.signature()));
        GameProfile gameProfile = new GameProfile(profile.id(), profile.name(), new PropertyMap(properties));
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getSkinManager().get(gameProfile).thenAccept(result ->
                minecraft.execute(() -> result.ifPresent(skin -> loaded.put(key, skin))));
    }

    public void tick() {
        if (dirty && System.currentTimeMillis() - changedAt >= SAVE_DELAY_MS) {
            save();
        }
    }

    public void save() {
        if (file == null || !dirty) {
            return;
        }
        dirty = false;
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(toJson()));
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.error("Не удалось сохранить головы в {}", file, e);
        }
    }

    private void load() {
        if (file == null || !Files.exists(file)) {
            return;
        }
        try {
            fromJson(JsonParser.parseString(Files.readString(file)));
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Не удалось прочитать головы из {}", file, e);
        }
    }

    JsonElement toJson() {
        JsonObject json = new JsonObject();
        profiles.values().forEach(profile -> {
            JsonObject item = new JsonObject();
            item.addProperty("id", profile.id().toString());
            item.addProperty("textures", profile.textures());
            if (profile.signature() != null) {
                item.addProperty("signature", profile.signature());
            }
            json.add(profile.name(), item);
        });
        return json;
    }

    void fromJson(JsonElement json) {
        profiles.clear();
        if (!json.isJsonObject()) {
            return;
        }
        json.getAsJsonObject().entrySet().forEach(entry -> {
            try {
                JsonObject item = entry.getValue().getAsJsonObject();
                String signature = item.has("signature") ? item.get("signature").getAsString() : null;
                profiles.put(key(entry.getKey()), new Profile(UUID.fromString(item.get("id").getAsString()),
                        entry.getKey(), item.get("textures").getAsString(), signature));
            } catch (RuntimeException e) {
                LOGGER.warn("Пропущена битая голова {}", entry.getKey(), e);
            }
        });
        trim();
    }

    private void trim() {
        while (profiles.size() > MAX_PROFILES) {
            String oldest = profiles.keySet().iterator().next();
            profiles.remove(oldest);
            loaded.remove(oldest);
        }
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
