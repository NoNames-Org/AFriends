package dev.no.name.blooddesigner.core.party;

import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.util.List;

public final class PartyService {
    private static final long INFO_WINDOW_MS = 3000;

    private static final long ACTION_WINDOW_MS = 3000;

    private static final long REFRESH_AFTER_ACTION_MS = 1000;

    private static final long AUTO_REFRESH_MS = 20_000;
    private static final long MIN_REFRESH_GAP_MS = 3000;
    private static final String NO_PLAYER = "No player was found";

    private static final List<String> KICK_VERBS = List.of("kick", "remove");

    private final PartyState state = new PartyState();
    private long hideInfoUntil;
    private long actionUntil;
    private long refreshAt;
    private long lastRefresh;
    private boolean panelOpen;

    public void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> state.reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            state.reset();
            refreshAt = 0;
            panelOpen = false;
        });
    }

    public PartyState state() {
        return state;
    }

    public void setPanelOpen(boolean open) {
        panelOpen = open;
        if (open && now() - lastRefresh > MIN_REFRESH_GAP_MS) {
            refresh();
        }
    }

    public void tick() {
        if (Minecraft.getInstance().getConnection() == null) {
            return;
        }
        long now = now();
        if (refreshAt > 0 && now >= refreshAt || panelOpen && now - lastRefresh > AUTO_REFRESH_MS) {
            refresh();
        }
    }

    public void refresh() {
        long now = now();
        refreshAt = 0;
        lastRefresh = now;
        if (send("party info")) {
            hideInfoUntil = now + INFO_WINDOW_MS;
        }
    }

    public void invite(String name) {
        action("party invite " + name);
    }

    public boolean kick(String name) {
        String verb = kickVerb();
        if (verb == null) {
            state.setStatus("На сервере нет команды кика из пати");
            return false;
        }
        action("party " + verb + " " + name);
        return true;
    }

    public boolean canKick() {
        return kickVerb() != null;
    }

    public void disband() {
        action("party disband");
    }

    public void leave() {
        action("party leave");
    }

    public void accept() {
        String command = state.inviteCommand();
        state.clearInvite();
        action(command != null ? command : "party accept");
    }

    public boolean consume(String plain, List<String> clickCommands) {
        long now = now();
        PartyEvent event = PartyMessages.parse(plain);
        if (event == null) {
            if (now < actionUntil && plain.strip().equalsIgnoreCase(NO_PLAYER)) {
                state.setStatus("Игрок не найден");
            }
            return false;
        }
        state.apply(event, now);
        if (event instanceof PartyEvent.IncomingInvite) {
            state.setInviteCommand(PartyMessages.acceptCommand(clickCommands));
        }
        boolean answer = event instanceof PartyEvent.Info || event instanceof PartyEvent.NotInParty;
        if (answer && now < hideInfoUntil) {
            hideInfoUntil = 0;
            return true;
        }
        if (!answer) {
            refreshAt = now + REFRESH_AFTER_ACTION_MS;
        }
        return false;
    }

    private void action(String command) {
        if (send(command)) {
            long now = now();
            actionUntil = now + ACTION_WINDOW_MS;
            refreshAt = now + REFRESH_AFTER_ACTION_MS;
        }
    }

    private static String kickVerb() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return null;
        }
        CommandNode<?> party = connection.getCommands().getRoot().getChild("party");
        if (party == null) {
            return null;
        }
        for (String verb : KICK_VERBS) {
            if (party.getChild(verb) != null) {
                return verb;
            }
        }
        return null;
    }

    private static boolean send(String command) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return false;
        }
        connection.sendCommand(command);
        return true;
    }

    private static long now() {
        return System.currentTimeMillis();
    }
}
