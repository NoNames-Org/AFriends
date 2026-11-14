package dev.no.name.blooddesigner.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.util.Locale;

public final class ServerScope {
    public static final String SINGLEPLAYER = "singleplayer";

    public static final String NONE = "none";
    private static final String DEFAULT_PORT = ":25565";

    private ServerScope() {
    }

    public static String current() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.hasSingleplayerServer()) {
            return SINGLEPLAYER;
        }
        ServerData server = minecraft.getCurrentServer();
        if (server == null || server.ip == null || server.ip.isBlank()) {
            return NONE;
        }
        return normalize(server.ip);
    }

    static String normalize(String address) {
        String key = address.strip().toLowerCase(Locale.ROOT);
        return key.endsWith(DEFAULT_PORT) ? key.substring(0, key.length() - DEFAULT_PORT.length()) : key;
    }
}
