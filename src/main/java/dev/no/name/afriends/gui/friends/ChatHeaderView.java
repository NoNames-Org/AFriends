package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.core.Friend;
import dev.no.name.afriends.core.FriendManager;
import dev.no.name.afriends.core.MessageRoute;
import dev.no.name.afriends.core.PrivateMessages;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.text.TextStyle;

import static dev.no.name.afriends.gui.friends.MessengerLayout.CHAT_WIDTH;
import static dev.no.name.afriends.gui.friends.MessengerLayout.CHAT_X;
import static dev.no.name.afriends.gui.friends.MessengerLayout.DANGER;
import static dev.no.name.afriends.gui.friends.MessengerLayout.HEADER_FACE;
import static dev.no.name.afriends.gui.friends.MessengerLayout.HEADER_HEIGHT;
import static dev.no.name.afriends.gui.friends.MessengerLayout.PAD;
import static dev.no.name.afriends.gui.friends.MessengerLayout.inside;

/** Шапка открытого чата: значок, название и строка состояния; при выделении сообщений — панель выделения. */
final class ChatHeaderView {
    private final ChatController controller;
    private final PrivateMessages messages;
    private final FriendManager friends;
    private final MessageRoute route;
    private final ChatTiles tiles;
    private final Selection selection;

    ChatHeaderView(ChatController controller, PrivateMessages messages, FriendManager friends, MessageRoute route,
                   ChatTiles tiles, Selection selection) {
        this.controller = controller;
        this.messages = messages;
        this.friends = friends;
        this.route = route;
        this.tiles = tiles;
        this.selection = selection;
    }

    void draw(Gfx g, int mouseX, int mouseY) {
        if (selection.isActive()) {
            selection.drawBar(g, CHAT_X + PAD + 2, 0, CHAT_WIDTH - PAD * 2 - 22, HEADER_HEIGHT, mouseX, mouseY);
            Draw.rect(g, CHAT_X + 1, HEADER_HEIGHT, CHAT_WIDTH - 2, 1, Theme.BORDER);
            return;
        }
        int faceX = CHAT_X + PAD + 2;
        int faceY = (HEADER_HEIGHT - HEADER_FACE) / 2;
        Line line = drawTile(g, controller.partner(), faceX, faceY);
        float textX = faceX + HEADER_FACE + 8;
        Draw.text(g, Draw.trim(line.title(), CHAT_WIDTH - 80), textX, 11, Theme.TEXT, TextStyle.TITLE);
        Draw.text(g, Draw.trim(line.status(), CHAT_WIDTH - 80), textX, 25, line.color(), TextStyle.SMALL);
        Draw.rect(g, CHAT_X + 1, HEADER_HEIGHT, CHAT_WIDTH - 2, 1, Theme.BORDER);
    }

    private record Line(String title, String status, int color) {
    }

    // значок чата и что написать рядом
    private Line drawTile(Gfx g, String partner, int faceX, int faceY) {
        if (controller.isAnnouncements()) {
            ChatTiles.announcements(g, faceX, faceY, HEADER_FACE);
            return new Line(ChatController.ANNOUNCEMENTS_TITLE, "Новости от AFriends · только чтение", Theme.TEXT_SOFT);
        }
        if (controller.isOfficial()) {
            ChatTiles.official(g, faceX, faceY, HEADER_FACE);
            return new Line(PrivateMessages.OFFICIAL_NAME, "Администрация мода · ответ видят только админы", Theme.TEXT_SOFT);
        }
        if (controller.isNotes()) {
            ChatTiles.notes(g, faceX, faceY, HEADER_FACE);
            return new Line(ChatController.NOTES_TITLE, "Твой личный чат для заметок", Theme.TEXT_SOFT);
        }
        if (controller.isGroup()) {
            tiles.group(g, partner, faceX, faceY, HEADER_FACE);
            String network = route.headerLine(partner);
            return new Line(messages.displayName(partner), network != null ? network : "Групповой чат", Theme.TEXT_SOFT);
        }
        tiles.player(g, partner, faceX, faceY, HEADER_FACE);
        boolean offline = messages.offlineAt(partner) > 0;
        Friend friend = friends.find(partner);
        String network = route.headerLine(partner);
        String status = network != null ? network : offline ? "Не в сети — сообщения не доставлены"
                : friend == null ? "Не в друзьях · через /msg сервера" : "Друг · через /msg сервера";
        return new Line(messages.displayName(partner), status, offline && network == null ? DANGER : Theme.TEXT_SOFT);
    }

    /** Клик по аватарке или нику открывает профиль; у служебных чатов профиля нет. */
    boolean opensProfile(double x, double y) {
        return controller.partner() != null && !selection.isActive() && !controller.isAnnouncements()
                && !controller.isNotes() && !controller.isOfficial()
                && inside(x, y, CHAT_X + PAD, 0, CHAT_WIDTH - PAD * 2 - 22, HEADER_HEIGHT);
    }
}
