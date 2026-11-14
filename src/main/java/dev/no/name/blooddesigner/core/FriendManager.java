package dev.no.name.blooddesigner.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.no.name.blooddesigner.BloodDesigner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class FriendManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(BloodDesigner.NAME);

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final int TICKS_PER_SCAN = 20;

    public enum AddResult { ADDED, ALREADY, INVALID, SELF }

    private final List<Friend> friends = new ArrayList<>();

    private final Map<String, PlayerSkin> skins = new HashMap<>();
    private int tickCounter;

    public AddResult add(String name) {
        if (!NAME.matcher(name).matches()) {
            return AddResult.INVALID;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.getGameProfile().name().equalsIgnoreCase(name)) {
            return AddResult.SELF;
        }
        if (find(name) != null) {
            return AddResult.ALREADY;
        }
        friends.add(new Friend(name));
        scan();
        return AddResult.ADDED;
    }

    public boolean remove(String name) {
        return friends.removeIf(friend -> friend.name().equalsIgnoreCase(name));
    }

    public Friend find(String name) {
        for (Friend friend : friends) {
            if (friend.name().equalsIgnoreCase(name)) {
                return friend;
            }
        }
        return null;
    }

    public PlayerSkin skinOf(Friend friend) {
        return skinOf(friend.name());
    }

    public PlayerSkin skinOf(String name) {
        PlayerSkin skin = skins.get(key(name));
        return skin != null ? skin : HeadCache.get().skin(name);
    }

    public List<Friend> sorted() {
        List<Friend> result = new ArrayList<>(friends);
        result.sort(Comparator.comparing(friend -> friend.name().toLowerCase(Locale.ROOT)));
        return List.copyOf(result);
    }

    public int size() {
        return friends.size();
    }

    public void tick() {
        if (++tickCounter >= TICKS_PER_SCAN) {
            tickCounter = 0;
            scan();
        }
    }

    private void scan() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return;
        }
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            skins.put(key(info.getProfile().name()), info.getSkin());

            HeadCache.get().remember(info);
        }
    }

    public static List<String> onlinePlayerNames() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return List.of();
        }
        return connection.getOnlinePlayers().stream().map(info -> info.getProfile().name()).toList();
    }

    public List<String> names() {
        return friends.stream().map(Friend::name).toList();
    }

    public JsonElement toJson() {
        JsonArray array = new JsonArray();
        for (Friend friend : friends) {
            JsonObject json = new JsonObject();
            json.addProperty("name", friend.name());
            array.add(json);
        }
        return array;
    }

    public void fromJson(JsonElement json) {
        friends.clear();
        if (!json.isJsonArray()) {
            return;
        }
        for (JsonElement element : json.getAsJsonArray()) {
            try {
                JsonObject object = element.getAsJsonObject();
                String name = object.get("name").getAsString();
                if (NAME.matcher(name).matches() && find(name) == null) {
                    friends.add(new Friend(name));
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Пропущен битый друг в конфиге: {}", element, e);
            }
        }
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
