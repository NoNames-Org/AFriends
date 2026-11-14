package dev.no.name.blooddesigner.gui.friends;

import dev.no.name.blooddesigner.compat.Compat;
import dev.no.name.blooddesigner.core.FriendManager;
import dev.no.name.blooddesigner.core.IgnoreList;
import dev.no.name.blooddesigner.core.PrivateMessages;
import dev.no.name.blooddesigner.core.SocialData;
import dev.no.name.blooddesigner.core.party.PartyService;
import dev.no.name.blooddesigner.core.party.PartyState;
import dev.no.name.blooddesigner.gui.hub.HubRail;
import dev.no.name.blooddesigner.gui.hub.PanelState;
import dev.no.name.blooddesigner.gui.hub.SettingsPanel;
import dev.no.name.blooddesigner.gui.party.PartyPanel;
import dev.no.name.blooddesigner.gui.render.icon.Icon;
import dev.no.name.blooddesigner.gui.theme.ThemeSettings;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.ChatScreen;

import java.util.List;

public final class FriendsUi {
    private static final int MARGIN = 4;
    private static final int GAP = 4;
    private static final int PANEL_WIDTH = 190;

    private static final int CHAT_INPUT_HEIGHT = 18;

    private final SocialData social;
    private final PartyService party;
    private final ThemeSettings theme;
    private final Runnable save;
    private final Runnable onHistoryChange;
    private final FriendsState friendsState = new FriendsState();
    private final PanelState partyState = new PanelState();
    private final PanelState settingsState = new PanelState();

    public FriendsUi(SocialData social, PartyService party, ThemeSettings theme, Runnable save, Runnable onHistoryChange) {
        this.social = social;
        this.party = party;
        this.theme = theme;
        this.save = save;
        this.onHistoryChange = onHistoryChange;
    }

    public void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof ChatScreen)) {
                return;
            }

            SocialData.Scope scope = social.current();
            MessengerScreen.Context context = new MessengerScreen.Context(scope.friends(), scope.messages(),
                    scope.ignored(), party, save);

            int railX = width - MARGIN - HubRail.WIDTH;
            int top = MARGIN;
            int panelHeight = height - top - CHAT_INPUT_HEIGHT - MARGIN;
            int panelX = railX - GAP - PANEL_WIDTH;

            FriendsPanel friendsPanel = new FriendsPanel(panelX, top, PANEL_WIDTH, panelHeight, scope.friends(),
                    scope.messages(), scope.ignored(), friendsState, save);
            PartyPanel partyPanel = new PartyPanel(panelX, top, PANEL_WIDTH, panelHeight, party, scope.friends(), partyState);
            SettingsPanel settingsPanel = new SettingsPanel(panelX, top, PANEL_WIDTH, panelHeight, theme, settingsState,
                    onHistoryChange);
            FriendContextMenu menu = new FriendContextMenu(panelX, friendsState, menuItems(context));
            HubRail rail = new HubRail(railX, top, panelHeight, railButtons(context), List.of(
                    new HubRail.Button(Icon.SETTINGS, "Настройки", settingsState::isOpen, () -> null,
                            () -> toggle(Tab.SETTINGS))));
            friendsState.closeMenu();
            party.setPanelOpen(partyState.isOpen());

            Compat.addWidget(screen, friendsPanel);
            Compat.addWidget(screen, partyPanel);
            Compat.addWidget(screen, settingsPanel);
            Compat.addWidget(screen, menu);
            Compat.addWidget(screen, rail);

            ScreenMouseEvents.allowMouseScroll(screen).register((current, mouseX, mouseY, horizontal, vertical) ->
                    !friendsPanel.scroll(mouseX, mouseY, vertical) && !partyPanel.isMouseOver(mouseX, mouseY)
                            && !settingsPanel.isMouseOver(mouseX, mouseY));

            ScreenKeyboardEvents.allowKeyPress(screen).register((current, event) ->
                    !(partyPanel.handleKey(event) || friendsPanel.handleKey(event)));
            ScreenEvents.remove(screen).register(current -> {
                partyPanel.blur();
                friendsPanel.blur();
                party.setPanelOpen(false);
            });

            ScreenMouseEvents.allowMouseClick(screen).register((current, event) -> {
                if (friendsState.menuFriend() != null && !menu.isMouseOver(event.x(), event.y())
                        && !friendsPanel.isMouseOver(event.x(), event.y())) {
                    friendsState.closeMenu();
                }
                return true;
            });
        });
    }

    private enum Tab { FRIENDS, PARTY, SETTINGS }

    private List<HubRail.Button> railButtons(MessengerScreen.Context context) {
        PrivateMessages messages = context.messages();
        PartyState partyInfo = party.state();
        return List.of(
                new HubRail.Button(Icon.USERS, "Друзья", friendsState::isOpen, () -> null, () -> toggle(Tab.FRIENDS)),
                new HubRail.Button(Icon.FLAG, "Пати", partyState::isOpen, () -> {
                    if (partyInfo.incomingInvite(System.currentTimeMillis()) != null) {
                        return "!";
                    }
                    int members = partyInfo.members().size();
                    return members > 1 ? String.valueOf(members) : null;
                }, () -> toggle(Tab.PARTY)),
                new HubRail.Button(Icon.MESSAGE, "Сообщения", () -> false, () -> {
                    int unread = messages.totalUnread();
                    return unread > 0 ? String.valueOf(unread) : null;
                }, () -> Compat.setScreen(new MessengerScreen(null, context))));
    }

    private void toggle(Tab tab) {
        boolean friendsOpen = tab == Tab.FRIENDS && !friendsState.isOpen();
        boolean partyOpen = tab == Tab.PARTY && !partyState.isOpen();
        boolean settingsOpen = tab == Tab.SETTINGS && !settingsState.isOpen();
        if (friendsOpen) {
            friendsState.open();
        } else {
            friendsState.close();
        }
        if (partyOpen) {
            partyState.open();
        } else {
            partyState.close();
        }
        if (settingsOpen) {
            settingsState.open();
        } else {
            settingsState.close();
        }
        party.setPanelOpen(partyOpen);
    }

    private List<FriendContextMenu.Item> menuItems(MessengerScreen.Context context) {
        FriendManager friends = context.friends();
        IgnoreList ignored = context.ignored();
        return List.of(
                new FriendContextMenu.Item(Icon.MESSAGE, friend -> "Написать в ЛС",
                        friend -> Compat.setScreen(new MessengerScreen(friend.name(), context)),
                        friend -> false),
                new FriendContextMenu.Item(Icon.TRASH, friend -> "Удалить из друзей",
                        friend -> {
                            friends.remove(friend.name());
                            save.run();
                        },
                        friend -> true),
                new FriendContextMenu.Item(Icon.BAN,
                        friend -> ignored.isIgnored(friend.name()) ? "Не игнорировать" : "Игнорировать",
                        friend -> {
                            ignored.toggle(friend.name());
                            save.run();
                        },
                        friend -> false));
    }
}
