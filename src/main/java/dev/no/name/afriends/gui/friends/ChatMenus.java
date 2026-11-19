package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.addon.AddonRegistry;
import dev.no.name.afriends.api.ComposeAction;
import dev.no.name.afriends.api.FriendAction;
import dev.no.name.afriends.core.FriendManager;
import dev.no.name.afriends.core.GroupChats;
import dev.no.name.afriends.core.GroupPermissions;
import dev.no.name.afriends.core.IgnoreList;
import dev.no.name.afriends.core.NotifySettings;
import dev.no.name.afriends.core.PrivateMessages;
import dev.no.name.afriends.gui.render.icon.Icon;
import dev.no.name.afriends.gui.sound.UiSound;
import dev.no.name.afriends.gui.sound.UiSounds;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import static dev.no.name.afriends.gui.friends.MessengerLayout.BAR_Y;
import static dev.no.name.afriends.gui.friends.MessengerLayout.HEIGHT;
import static dev.no.name.afriends.gui.friends.MessengerLayout.WIDTH;

/** Меню правого клика по чату в списке и меню кнопки дополнения у поля ввода. */
final class ChatMenus {
    private final MessengerScreen.Context context;
    private final PrivateMessages messages;
    private final AddonRegistry addons;
    private final ChatController controller;
    private final ChatNavigator navigator;
    private final MessengerPopups popups;
    // отправить вариант из меню дополнения тем же путём, что и текст из поля
    private final Predicate<String> sendText;

    ChatMenus(MessengerScreen.Context context, ChatController controller, ChatNavigator navigator,
              MessengerPopups popups, Predicate<String> sendText) {
        this.context = context;
        this.messages = context.messages();
        this.addons = context.ui().addons();
        this.controller = controller;
        this.navigator = navigator;
        this.popups = popups;
        this.sendText = sendText;
    }

    void openChatMenu(String entry, double mouseX, double mouseY) {
        if (ChatController.ANNOUNCEMENTS.equals(entry)) {
            return;
        }
        List<ChatMenu.Item> items = new ArrayList<>();
        // AFriends нельзя игнорировать, закрепить или удалить из друзей — только почистить у себя
        if (PrivateMessages.isOfficial(entry)) {
            items.add(clearItem(entry, true, controller::isOfficial));
            items.add(muteItem(entry));
            open(PrivateMessages.OFFICIAL_NAME, items, mouseX, mouseY);
            return;
        }
        if (PrivateMessages.NOTES.equals(entry)) {
            items.add(clearItem(PrivateMessages.NOTES, true, controller::isNotes));
            open(ChatController.NOTES_TITLE, items, mouseX, mouseY);
            return;
        }
        if (PrivateMessages.isGroup(entry)) {
            openGroupMenu(entry, items, mouseX, mouseY);
            return;
        }
        String name = messages.displayName(entry);
        items.add(friendItem(name));
        IgnoreList ignored = context.ignored();
        boolean isIgnored = ignored.isIgnored(name);
        items.add(new ChatMenu.Item(Icon.BAN, isIgnored ? "Не игнорировать" : "Игнорировать", false, () -> {
            ignored.toggle(name);
            context.save().run();
        }));
        addonItems(name, items);
        items.add(muteItem(name));
        items.add(archiveItem(name));
        items.add(pinItem(name));
        items.add(clearItem(name, false, () -> name.equalsIgnoreCase(controller.partner())));
        items.add(new ChatMenu.Item(Icon.TRASH, "Удалить чат", true, () -> {
            messages.delete(name);
            if (name.equalsIgnoreCase(controller.partner())) {
                navigator.select(null);
            }
        }));
        open(name, items, mouseX, mouseY);
    }

    private ChatMenu.Item friendItem(String name) {
        FriendManager friends = context.friends();
        boolean friend = friends.find(name) != null;
        return new ChatMenu.Item(friend ? Icon.USER_OFF : Icon.USERS, friend ? "Удалить из друзей" : "Добавить в друзья",
                friend, () -> {
                    if (friend) {
                        friends.remove(name);
                        addons.removed(name);
                    } else if (friends.add(name) == FriendManager.AddResult.ADDED) {
                        addons.added(name, controller::info);
                    }
                    context.save().run();
                });
    }

    private void openGroupMenu(String chat, List<ChatMenu.Item> items, double mouseX, double mouseY) {
        GroupChats groups = controller.groups();
        int perms = groups == null ? 0 : groups.myPerms(chat);
        items.add(new ChatMenu.Item(Icon.USERS, "Участники", false, () -> popups.openGroupEditor(() ->
                popups.profilePanel().open(chat, ProfilePanel.Tab.MEMBERS, WIDTH, HEIGHT))));
        if (GroupPermissions.has(perms, GroupPermissions.ADD)) {
            items.add(new ChatMenu.Item(Icon.PLUS, "Позвать друга", false,
                    () -> popups.openGroupEditor(() -> popups.openInvite(chat))));
        }
        if (GroupPermissions.has(perms, GroupPermissions.TITLE)) {
            items.add(new ChatMenu.Item(Icon.EDIT, "Переименовать", false, () -> popups.openGroupEditor(() ->
                    popups.groupEditor().openRename(chat, messages.displayName(chat), WIDTH, HEIGHT))));
        }
        if (GroupPermissions.has(perms, GroupPermissions.POLLS)) {
            items.add(new ChatMenu.Item(Icon.BAR_CHART, "Создать опрос", false, () -> popups.openGroupEditor(() ->
                    popups.pollComposer().open(chat, WIDTH, HEIGHT))));
        }
        items.add(muteItem(chat));
        items.add(archiveItem(chat));
        items.add(pinItem(chat));
        items.add(clearItem(chat, false, () -> chat.equals(controller.partner())));
        items.add(new ChatMenu.Item(Icon.LOG_OUT, "Выйти из группы", true,
                () -> popups.confirmLeave(chat, messages.displayName(chat))));
        open(messages.displayName(chat), items, mouseX, mouseY);
    }

    // пункты аддонов из меню друга — и в меню переписки с ним (например, «Позвать в пати»)
    private void addonItems(String name, List<ChatMenu.Item> items) {
        for (FriendAction action : addons.actions()) {
            if (addons.guard("friendAction", () -> action.visible().test(name), false)) {
                items.add(new ChatMenu.Item(addons.guard("friendAction", action::icon, Icon.CUBE),
                        addons.guard("friendAction", () -> action.label().apply(name), ""),
                        addons.guard("friendAction", action::danger, false),
                        () -> addons.run("friendAction", () -> action.action().accept(name))));
            }
        }
    }

    // «Очистить чат»; открытый этот же чат перечитывается
    private ChatMenu.Item clearItem(String chat, boolean danger, BooleanSupplier isOpen) {
        return new ChatMenu.Item(Icon.BRUSH, "Очистить чат", danger, () -> {
            messages.clear(chat);
            if (isOpen.getAsBoolean()) {
                navigator.select(controller.partner());
            }
        });
    }

    private ChatMenu.Item pinItem(String chat) {
        boolean pinned = messages.isPinned(chat);
        return new ChatMenu.Item(Icon.PIN, pinned ? "Открепить чат" : "Закрепить чат", false, () -> messages.togglePin(chat));
    }

    // «Выключить звук» у чата, как в Telegram: сообщения приходят, но без звука
    private ChatMenu.Item muteItem(String chat) {
        NotifySettings alerts = context.alerts();
        boolean muted = alerts.isMuted(chat);
        return new ChatMenu.Item(muted ? Icon.BELL : Icon.BELL_OFF, muted ? "Включить звук" : "Выключить звук", false,
                () -> alerts.toggleMuted(chat));
    }

    private ChatMenu.Item archiveItem(String chat) {
        boolean inArchive = messages.isArchived(chat);
        return new ChatMenu.Item(Icon.ARCHIVE, inArchive ? "Вернуть из архива" : "В архив", false, () -> {
            messages.toggleArchived(chat);
            controller.info(inArchive ? "Чат возвращён из архива" : "Чат в архиве — он вернётся, когда придёт сообщение");
        });
    }

    private void open(String title, List<ChatMenu.Item> items, double mouseX, double mouseY) {
        popups.menu().open(title, items, (float) mouseX, (float) mouseY, WIDTH, HEIGHT);
    }

    // кнопка дополнения у поля ввода: список вариантов, выбранный уходит сообщением
    void openCompose(ComposeAction action, int buttonX) {
        String partner = controller.partner();
        if (partner == null) {
            return;
        }
        List<ChatMenu.Item> items = new ArrayList<>();
        for (ComposeAction.Option option : addons.guard("composeAction", () -> action.options(partner), List.<ComposeAction.Option>of())) {
            items.add(new ChatMenu.Item(option.icon(), option.label(), false, () -> sendText.test(option.text())));
        }
        if (items.isEmpty()) {
            items.add(new ChatMenu.Item(Icon.INFO, addons.guard("composeAction", () -> action.empty(partner), ""), false, () -> { }));
        }
        String title = addons.guard("composeAction", () -> action.title(partner), "");
        popups.menu().open(title, items, buttonX, BAR_Y, WIDTH, HEIGHT);
        UiSounds.play(UiSound.DROPDOWN_OPEN);
    }
}
