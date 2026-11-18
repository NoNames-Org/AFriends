package dev.no.name.afriends.core;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatFilter {
    private static final Pattern VANILLA_CHAT = Pattern.compile("^<([A-Za-z0-9_]{3,16})> ");

    private static final Pattern NAME_BEFORE_COLON = Pattern.compile("([A-Za-z0-9_]{3,16})\\s*$");

    private static final int MAX_PREFIX = 60;

    private final SocialData social;
    public ChatFilter(SocialData social) {
        this.social = social;
    }

    public void register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay) {
                return true;
            }
            String plain = message.getString();

            String offline = ServerMessages.offlinePlayer(plain);
            if (offline != null) {
                social.messages().markOffline(offline, System.currentTimeMillis());
                return !social.messages().isOpen(offline);
            }
            PrivateMessages.Parsed parsed = PrivateMessages.parse(plain, selfName(), System.currentTimeMillis());
            if (parsed == null) {
                return !social.ignored().isIgnored(chatSender(plain));
            }
            if (!parsed.message().fromMe() && social.ignored().isIgnored(parsed.partner())) {
                return false;
            }
            social.messages().add(parsed.partner(), parsed.message());
            return true;
        });

        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, time) ->
                sender == null || !social.ignored().isIgnored(sender.name()));
    }

    static String chatSender(String plain) {
        Matcher vanilla = VANILLA_CHAT.matcher(plain);
        if (vanilla.find()) {
            return vanilla.group(1);
        }
        int colon = plain.indexOf(':');
        if (colon <= 0 || colon > MAX_PREFIX) {
            return null;
        }
        Matcher name = NAME_BEFORE_COLON.matcher(plain.substring(0, colon));
        return name.find() ? name.group(1) : null;
    }

    private static String selfName() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? null : player.getGameProfile().name();
    }
}
