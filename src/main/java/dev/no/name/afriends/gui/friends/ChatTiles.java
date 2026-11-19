package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.addon.AddonRegistry;
import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.core.FriendManager;
import dev.no.name.afriends.core.PrivateMessages;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.media.ChatMedia;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.icon.Icon;
import net.minecraft.world.entity.player.PlayerSkin;

/** Значки чатов: объявления, AFriends, избранное, группа или голова игрока. Список, шапка и окно пересылки. */
final class ChatTiles {
    private static final int NOTES_COLOR = 0xFF3D7BFF;
    private static final int ANNOUNCEMENTS_COLOR = 0xFFE0313F;
    /** Чат AFriends: переписка с админами мода через портал. */
    private static final int OFFICIAL_COLOR = 0xFFB3122A;

    private final FriendManager friends;
    private final PrivateMessages messages;
    private final AddonRegistry addons;
    private final ChatMedia chatMedia;

    ChatTiles(FriendManager friends, PrivateMessages messages, AddonRegistry addons, ChatMedia chatMedia) {
        this.friends = friends;
        this.messages = messages;
        this.addons = addons;
        this.chatMedia = chatMedia;
    }

    // значок чата: объявления, AFriends, избранное или голова игрока (список чатов и окно пересылки)
    void entry(Gfx g, String entry, float x, float y, int size) {
        if (ChatController.ANNOUNCEMENTS.equals(entry)) {
            announcements(g, x, y, size);
        } else if (PrivateMessages.isOfficial(entry)) {
            official(g, x, y, size);
        } else if (PrivateMessages.NOTES.equals(entry)) {
            notes(g, x, y, size);
        } else if (PrivateMessages.isGroup(entry)) {
            group(g, entry, x, y, size);
        } else {
            player(g, entry, x, y, size);
        }
    }

    // голова игрока и зелёная точка, если он в сети по данным дополнения
    void player(Gfx g, String name, float x, float y, int size) {
        face(g, friends.skinOf(name), (int) x, (int) y, size);
        if (OnlineDot.isOnline(addons, name)) {
            OnlineDot.draw(g, x, y, size);
        }
    }

    // аватарка группы, а пока её нет — синий кружок со значком
    void group(Gfx g, String chat, float x, float y, int size) {
        PanelKit.groupTile(g, messages.avatar(chat), chatMedia, x, y, size);
    }

    PlayerSkin skinOf(String name) {
        return friends.skinOf(name);
    }

    static void announcements(Gfx g, float x, float y, int size) {
        colored(g, Icon.BELL, ANNOUNCEMENTS_COLOR, x, y, size);
    }

    static void official(Gfx g, float x, float y, int size) {
        colored(g, Icon.DROPLET, OFFICIAL_COLOR, x, y, size);
    }

    static void notes(Gfx g, float x, float y, int size) {
        colored(g, Icon.HEART, NOTES_COLOR, x, y, size);
    }

    // круглый значок служебной строки списка
    static void round(Gfx g, Icon icon, int color, float x, float y, int size) {
        Draw.roundedRect(g, x, y, size, size, size / 2f, color);
        float iconSize = size * 0.55f;
        Draw.icon(g, icon, x + (size - iconSize) / 2, y + (size - iconSize) / 2, iconSize, 0xFFFFFFFF);
    }

    static void face(Gfx g, PlayerSkin skin, int x, int y, int size) {
        if (skin != null) {
            g.face(skin, x, y, size);
        } else {
            Draw.gradient(g, x, y, size, size, Theme.RADIUS - 1, Theme.TILE_ON, Theme.TILE);
            float icon = size * 0.5f;
            Draw.icon(g, Icon.USER, x + (size - icon) / 2, y + (size - icon) / 2, icon, Theme.TEXT_DIM);
        }
    }

    // скруглённый квадрат цвета чата с белым значком
    private static void colored(Gfx g, Icon icon, int color, float x, float y, int size) {
        Draw.roundedRect(g, x, y, size, size, Theme.RADIUS, color);
        float iconSize = size * 0.55f;
        Draw.icon(g, icon, x + (size - iconSize) / 2, y + (size - iconSize) / 2, iconSize, 0xFFFFFFFF);
    }
}
