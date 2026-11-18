package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.addon.Addons;
import dev.no.name.afriends.api.FriendAction;
import dev.no.name.afriends.api.RailPanel;
import dev.no.name.afriends.api.SidePanel;
import dev.no.name.afriends.compat.Compat;
import dev.no.name.afriends.compat.WidgetBase;
import dev.no.name.afriends.core.FriendManager;
import dev.no.name.afriends.core.IgnoreList;
import dev.no.name.afriends.core.NotifySettings;
import dev.no.name.afriends.core.PrivateMessages;
import dev.no.name.afriends.core.SocialData;
import dev.no.name.afriends.core.SoundSettings;
import dev.no.name.afriends.gui.hub.HubRail;
import dev.no.name.afriends.gui.hub.PanelState;
import dev.no.name.afriends.gui.render.icon.Icon;
import dev.no.name.afriends.gui.settings.SettingsScreen;
import dev.no.name.afriends.gui.theme.ThemeSettings;
import dev.no.name.afriends.net.game.LinkSettings;
import dev.no.name.afriends.net.game.NetworkGame;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Панель кнопок справа от чата: «Друзья», панели аддонов, «Сообщения» и внизу «Настройки».
 * Открыта всегда одна боковая панель — встроенная «Друзья» или панель аддона.
 */
public final class FriendsUi {
    private static final int MARGIN = 4;
    private static final int GAP = 4;
    private static final int PANEL_WIDTH = 190;
    private static final int CHAT_INPUT_HEIGHT = 18;

    private final SocialData social;
    private final ThemeSettings theme;
    private final LinkSettings linkSettings;
    private final NotifySettings notify;
    private final SoundSettings sound;
    private final NetworkGame network;
    private final Runnable save;
    private final Runnable onHistoryChange;
    private final FriendsState friendsState = new FriendsState();
    // состояние панели аддона живёт дольше окна чата: открытая панель остаётся открытой в следующий раз
    private final Map<RailPanel, PanelState> addonStates = new IdentityHashMap<>();

    public FriendsUi(SocialData social, ThemeSettings theme, LinkSettings linkSettings, NotifySettings notify,
                     SoundSettings sound, NetworkGame network, Runnable save, Runnable onHistoryChange) {
        this.social = social;
        this.theme = theme;
        this.linkSettings = linkSettings;
        this.notify = notify;
        this.sound = sound;
        this.network = network;
        this.save = save;
        this.onHistoryChange = onHistoryChange;
    }

    /** Панель аддона, открытая в этом окне чата. */
    private record Mounted(RailPanel panel, PanelState state, SidePanel side, WidgetBase widget) {
    }

    public void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof ChatScreen) {
                attach(screen, width, height);
            }
        });
    }

    private void attach(Screen screen, int width, int height) {
        SocialData.Scope scope = social.current();
        MessengerScreen.Context context = new MessengerScreen.Context(scope.friends(), scope.messages(),
                scope.ignored(), network.direct(), save, notify, onHistoryChange);

        int railX = width - MARGIN - HubRail.WIDTH;
        int top = MARGIN;
        int panelHeight = height - top - CHAT_INPUT_HEIGHT - MARGIN;
        int panelX = railX - GAP - PANEL_WIDTH;

        FriendsPanel friendsPanel = new FriendsPanel(panelX, top, PANEL_WIDTH, panelHeight, scope.friends(),
                scope.messages(), scope.ignored(), friendsState, save);
        List<Mounted> mounted = mountAddonPanels(new RailPanel.Bounds(panelX, top, PANEL_WIDTH, panelHeight));
        FriendContextMenu menu = new FriendContextMenu(panelX, friendsState, menuItems(context));
        HubRail rail = new HubRail(railX, top, panelHeight, railButtons(context, mounted), List.of(
                new HubRail.Button(Icon.SETTINGS, "Настройки", () -> false, () -> null,
                        () -> Compat.setScreen(new SettingsScreen(screen, theme, linkSettings, sound, network.link())))));
        friendsState.closeMenu();

        Compat.addWidget(screen, friendsPanel);
        mounted.forEach(entry -> Compat.addWidget(screen, entry.widget()));
        Compat.addWidget(screen, menu);
        Compat.addWidget(screen, rail);
        mounted.forEach(entry -> notifyOpened(entry, entry.state().isOpen()));

        ScreenMouseEvents.allowMouseScroll(screen).register((current, mouseX, mouseY, horizontal, vertical) ->
                !friendsPanel.scroll(mouseX, mouseY, vertical) && mounted.stream().noneMatch(entry ->
                        Addons.get().guard("sidePanel", () -> entry.side().scroll(mouseX, mouseY, vertical), false)));
        ScreenKeyboardEvents.allowKeyPress(screen).register((current, event) ->
                !(mounted.stream().anyMatch(entry ->
                        Addons.get().guard("sidePanel", () -> entry.side().handleKey(event), false))
                        || friendsPanel.handleKey(event)));
        ScreenEvents.remove(screen).register(current -> {
            friendsPanel.blur();
            mounted.forEach(entry -> {
                blur(entry);
                notifyOpened(entry, false);
            });
        });
        ScreenMouseEvents.allowMouseClick(screen).register((current, event) -> {
            if (friendsState.menuFriend() != null && !menu.isMouseOver(event.x(), event.y())
                    && !friendsPanel.isMouseOver(event.x(), event.y())) {
                friendsState.closeMenu();
            }
            // клик мимо панели гасит её поле: иначе оно и дальше забирает Backspace и стрелки у строки чата
            if (!friendsPanel.isMouseOver(event.x(), event.y())) {
                friendsPanel.blur();
            }
            for (Mounted entry : mounted) {
                if (!entry.widget().isMouseOver(event.x(), event.y())) {
                    blur(entry);
                }
            }
            return true;
        });
        // клик по кнопке или панели без поля ввода не уводит ввод из строки чата
        ScreenMouseEvents.afterMouseClick(screen).register((current, event, consumed) -> {
            if (current.getFocused() instanceof WidgetBase widget && !widget.wantsTextInput()) {
                focusChatInput(current);
            }
            return consumed;
        });
    }

    // панели аддонов, которые сейчас видны; скрытые закрываем, чтобы не открылись сами при возвращении кнопки
    private List<Mounted> mountAddonPanels(RailPanel.Bounds bounds) {
        Addons addons = Addons.get();
        List<Mounted> mounted = new ArrayList<>();
        for (RailPanel panel : addons.railPanels()) {
            PanelState state = addonStates.computeIfAbsent(panel, key -> new PanelState());
            if (!addons.guard("railPanel", panel::visible, false)) {
                state.close();
                continue;
            }
            SidePanel side = addons.guard("railPanel", () -> panel.create(bounds, state), null);
            WidgetBase widget = side == null ? null : addons.guard("sidePanel", side::widget, null);
            if (widget != null) {
                mounted.add(new Mounted(panel, state, side, widget));
            } else {
                state.close();
            }
        }
        return List.copyOf(mounted);
    }

    private static void focusChatInput(Screen screen) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof EditBox input) {
                screen.setFocused(input);
                return;
            }
        }
    }

    private List<HubRail.Button> railButtons(MessengerScreen.Context context, List<Mounted> mounted) {
        Addons addons = Addons.get();
        PrivateMessages messages = context.messages();
        List<HubRail.Button> buttons = new ArrayList<>();
        buttons.add(new HubRail.Button(Icon.USERS, "Друзья", friendsState::isOpen,
                () -> addons.requests().isEmpty() ? null : "!", () -> toggleFriends(mounted)));
        for (Mounted entry : mounted) {
            RailPanel panel = entry.panel();
            buttons.add(new HubRail.Button(addons.guard("railPanel", panel::icon, Icon.CUBE), addons.guard("railPanel", panel::label, ""),
                    entry.state()::isOpen, () -> addons.guard("railPanel", panel::badge, null),
                    () -> toggleAddon(entry, mounted)));
        }
        buttons.add(new HubRail.Button(Icon.MESSAGE, "Сообщения", () -> false, () -> {
            int unread = messages.totalUnread();
            return unread > 0 ? String.valueOf(unread) : null;
        }, () -> Compat.setScreen(new MessengerScreen(null, context))));
        return List.copyOf(buttons);
    }

    private void toggleFriends(List<Mounted> mounted) {
        boolean open = !friendsState.isOpen();
        mounted.forEach(entry -> setOpen(entry, false));
        if (open) {
            friendsState.open();
        } else {
            friendsState.close();
        }
    }

    private void toggleAddon(Mounted target, List<Mounted> mounted) {
        boolean open = !target.state().isOpen();
        friendsState.close();
        mounted.forEach(entry -> setOpen(entry, entry == target && open));
    }

    private static void setOpen(Mounted entry, boolean open) {
        if (entry.state().isOpen() == open) {
            return;
        }
        if (open) {
            entry.state().open();
        } else {
            entry.state().close();
            blur(entry);
        }
        notifyOpened(entry, open);
    }

    private static void blur(Mounted entry) {
        Addons.get().run("sidePanel", () -> entry.side().blur());
    }

    private static void notifyOpened(Mounted entry, boolean open) {
        Addons.get().run("sidePanel", () -> entry.side().opened(open));
    }

    private List<FriendContextMenu.Item> menuItems(MessengerScreen.Context context) {
        FriendManager friends = context.friends();
        IgnoreList ignored = context.ignored();
        Addons addons = Addons.get();
        List<FriendContextMenu.Item> items = new ArrayList<>();
        // пункты аддонов — сверху
        for (FriendAction action : addons.actions()) {
            items.add(new FriendContextMenu.Item(addons.guard("friendAction", action::icon, Icon.CUBE),
                    friend -> addons.guard("friendAction", () -> action.label().apply(friend.name()), ""),
                    friend -> addons.run("friendAction", () -> action.action().accept(friend.name())),
                    friend -> addons.guard("friendAction", action::danger, false),
                    friend -> addons.guard("friendAction", () -> action.visible().test(friend.name()), false)));
        }
        items.addAll(List.of(
                new FriendContextMenu.Item(Icon.MESSAGE, friend -> "Написать в ЛС",
                        friend -> Compat.setScreen(new MessengerScreen(friend.name(), context)),
                        friend -> false,
                        // друг по сети достижим откуда угодно, остальные — только на этом сервере
                        friend -> addons.reachable(friend.name()) || context.route().viaNetwork(friend.name())),
                new FriendContextMenu.Item(Icon.TRASH, friend -> "Удалить из друзей",
                        friend -> {
                            friends.remove(friend.name());
                            save.run();
                            addons.removed(friend.name());
                        },
                        friend -> true),
                new FriendContextMenu.Item(Icon.BAN,
                        friend -> ignored.isIgnored(friend.name()) ? "Не игнорировать" : "Игнорировать",
                        friend -> {
                            ignored.toggle(friend.name());
                            save.run();
                        },
                        friend -> false)));
        return items;
    }
}
