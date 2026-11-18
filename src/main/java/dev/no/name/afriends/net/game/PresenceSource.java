package dev.no.name.afriends.net.game;

import dev.no.name.afriends.net.model.Presence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

// где сейчас игрок — читается на потоке игры и дальше живёт неизменяемым Presence
final class PresenceSource {
    private Presence.Where lastWhere;
    private String lastServer = "";
    private long since = System.currentTimeMillis();

    Presence current(Minecraft client, boolean shareServer, String status) {
        Presence.Where where;
        String server = "";
        ServerData data = client.getCurrentServer();
        if (client.level == null) {
            where = Presence.Where.MENU;
        } else if (client.isLocalServer()) {
            where = Presence.Where.SINGLEPLAYER;
        } else if (data != null) {
            where = Presence.Where.SERVER;
            server = data.ip;
        } else {
            where = Presence.Where.MENU;
        }
        // «с какого момента» сбрасывается только при смене места
        if (where != lastWhere || !server.equals(lastServer)) {
            lastWhere = where;
            lastServer = server;
            since = System.currentTimeMillis();
        }
        if (!shareServer && where != Presence.Where.MENU) {
            return new Presence(Presence.Where.HIDDEN, "", status, since);
        }
        return new Presence(where, server, status, since);
    }
}
