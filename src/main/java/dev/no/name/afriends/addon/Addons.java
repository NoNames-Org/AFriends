package dev.no.name.afriends.addon;

import dev.no.name.afriends.AFriends;
import dev.no.name.afriends.api.AFriendsAddon;
import dev.no.name.afriends.api.AFriendsApi;
import dev.no.name.afriends.api.ComposeAction;
import dev.no.name.afriends.api.FriendAction;
import dev.no.name.afriends.api.FriendListener;
import dev.no.name.afriends.api.FriendRequests;
import dev.no.name.afriends.api.FriendStatus;
import dev.no.name.afriends.api.Friends;
import dev.no.name.afriends.api.MessageCard;
import dev.no.name.afriends.api.Privacy;
import dev.no.name.afriends.api.ProfileStatus;
import dev.no.name.afriends.api.RailPanel;
import dev.no.name.afriends.api.SendCheck;
import dev.no.name.afriends.api.SettingsCategory;
import dev.no.name.afriends.core.FriendManager;
import dev.no.name.afriends.core.HeadCache;
import dev.no.name.afriends.core.MessageRoute;
import dev.no.name.afriends.core.ServerScope;
import dev.no.name.afriends.core.SocialData;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.player.PlayerSkin;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

// Аддоны других модов и всё, что они встроили. Окна AFriends спрашивают тут;
// ошибка в аддоне пишется в лог один раз и не роняет игру
public final class Addons implements AFriendsApi {
    private static final Logger LOGGER = LoggerFactory.getLogger(AFriends.NAME);
    private static final String ENTRYPOINT = AFriends.MOD_ID;
    private static Addons instance = new Addons(null, () -> { }, null);

    public record Request(String name, String hint, FriendRequests owner) {
    }

    private final SocialData social;
    private final Runnable save;
    private final MessageRoute route;
    private final List<SendCheck> sendChecks = new CopyOnWriteArrayList<>();
    private final List<FriendStatus> statuses = new CopyOnWriteArrayList<>();
    private final List<FriendAction> actions = new CopyOnWriteArrayList<>();
    private final List<FriendListener> listeners = new CopyOnWriteArrayList<>();
    private final List<FriendRequests> requests = new CopyOnWriteArrayList<>();
    private final List<MessageCard> cards = new CopyOnWriteArrayList<>();
    private final List<ComposeAction> composeActions = new CopyOnWriteArrayList<>();
    private final List<RailPanel> railPanels = new CopyOnWriteArrayList<>();
    private final List<SettingsCategory> settingsCategories = new CopyOnWriteArrayList<>();
    // о какой точке расширения уже написали в лог; зовётся и с потоков сети
    private final Set<String> reported = ConcurrentHashMap.newKeySet();
    private volatile Privacy privacy;
    private volatile ProfileStatus profile;

    private Addons(SocialData social, Runnable save, MessageRoute route) {
        this.social = social;
        this.save = save;
        this.route = route;
    }

    // при запуске: свои встроенные части (сеть) первыми, потом дополнения других модов
    public static void load(SocialData social, Runnable save, MessageRoute route, List<AFriendsAddon> builtIn) {
        Addons addons = new Addons(social, save, route);
        instance = addons;
        builtIn.forEach(addon -> addon.register(addons));
        for (EntrypointContainer<AFriendsAddon> container
                : FabricLoader.getInstance().getEntrypointContainers(ENTRYPOINT, AFriendsAddon.class)) {
            String mod = container.getProvider().getMetadata().getId();
            try {
                container.getEntrypoint().register(addons);
                LOGGER.info("{}: подключён аддон {}", AFriends.NAME, mod);
            } catch (RuntimeException | LinkageError e) {
                LOGGER.error("{}: аддон {} не подключился", AFriends.NAME, mod, e);
            }
        }
    }

    public static Addons get() {
        return instance;
    }

    // ---- для аддонов ----

    @Override
    public void addRailPanel(RailPanel panel) {
        railPanels.add(panel);
    }

    @Override
    public void addSettingsCategory(SettingsCategory category) {
        settingsCategories.add(category);
    }

    @Override
    public void addSendCheck(SendCheck check) {
        sendChecks.add(check);
    }

    @Override
    public void setPrivacy(Privacy value) {
        privacy = value;
    }

    @Override
    public void addFriendStatus(FriendStatus status) {
        statuses.add(status);
    }

    @Override
    public void addFriendAction(FriendAction action) {
        actions.add(action);
    }

    @Override
    public void addFriendListener(FriendListener listener) {
        listeners.add(listener);
    }

    @Override
    public void addFriendRequests(FriendRequests value) {
        requests.add(value);
    }

    @Override
    public void setProfileStatus(ProfileStatus status) {
        profile = status;
    }

    @Override
    public void addMessageCard(MessageCard card) {
        cards.add(card);
    }

    @Override
    public void addComposeAction(ComposeAction action) {
        composeActions.add(action);
    }

    @Override
    public Friends friends() {
        return new Friends() {
            @Override
            public List<String> names() {
                return current() == null ? List.of() : current().names();
            }

            @Override
            public boolean contains(String name) {
                return current() != null && current().find(name) != null;
            }

            @Override
            public boolean add(String name) {
                FriendManager friends = current();
                if (friends == null || friends.add(name) != FriendManager.AddResult.ADDED) {
                    return false;
                }
                save.run();
                return true;
            }

            @Override
            public boolean online(String name) {
                FriendStatus.Status status = status(name);
                return status != null && status.online() || nearby(name);
            }

            @Override
            public boolean nearby(String name) {
                return FriendManager.onlinePlayerNames().stream().anyMatch(player -> player.equalsIgnoreCase(name));
            }

            @Override
            public PlayerSkin skin(String name) {
                FriendManager friends = current();
                return friends != null ? friends.skinOf(name) : HeadCache.get().skin(name);
            }

            @Override
            public List<String> nearbyNames() {
                return FriendManager.onlinePlayerNames();
            }
        };
    }

    @Override
    public Optional<String> sendMessage(String partner, String text) {
        if (route == null) {
            return Optional.of("AFriends ещё не готов");
        }
        return route.send(partner, text, problem -> { });
    }

    @Override
    public boolean viaNetwork(String partner) {
        return route != null && route.viaNetwork(partner);
    }

    private FriendManager current() {
        return social == null || ServerScope.NONE.equals(ServerScope.current()) ? null : social.friends();
    }

    // ---- для окон AFriends ----

    public List<RailPanel> railPanels() {
        return railPanels;
    }

    public List<SettingsCategory> settingsCategories() {
        return settingsCategories;
    }

    public Optional<String> sendProblem(String partner, String command) {
        for (SendCheck check : sendChecks) {
            Optional<String> problem = guard("sendCheck", () -> check.problem(partner, command), Optional.<String>empty());
            if (problem.isPresent()) {
                return problem;
            }
        }
        return Optional.empty();
    }

    public boolean hideMessages() {
        Privacy current = privacy;
        return current != null && guard("privacy", current::hideMessages, false);
    }

    public FriendStatus.Status status(String name) {
        for (FriendStatus status : statuses) {
            FriendStatus.Status result = guard("friendStatus", () -> status.status(name), null);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    // можно ли писать в ЛС и звать в пати; без дополнений — всегда да
    public boolean reachable(String name) {
        FriendStatus.Status status = status(name);
        return status == null || status.reachable();
    }

    public List<FriendAction> actions() {
        return actions;
    }

    public void run(String what, Runnable action) {
        guard(what, () -> {
            action.run();
            return null;
        }, null);
    }

    public void added(String name, Consumer<String> notice) {
        listeners.forEach(listener -> run("friendListener", () -> listener.added(name, notice)));
    }

    public void removed(String name) {
        listeners.forEach(listener -> run("friendListener", () -> listener.removed(name)));
    }

    public List<Request> requests() {
        List<Request> result = new ArrayList<>();
        for (FriendRequests owner : requests) {
            List<String> names = guard("friendRequests", owner::names, List.<String>of());
            String hint = guard("friendRequests", owner::hint, "");
            names.forEach(name -> result.add(new Request(name, hint, owner)));
        }
        return result;
    }

    public ProfileStatus profile() {
        ProfileStatus current = profile;
        return current != null && guard("profileStatus", current::active, false) ? current : null;
    }

    public MessageCard.Card card(String partner, String text, boolean fromMe) {
        for (MessageCard card : cards) {
            MessageCard.Card result = guard("messageCard", () -> card.card(partner, text, fromMe), null);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    public List<ComposeAction> composeActions() {
        return composeActions;
    }

    // вызов кода дополнения: при ошибке значение по умолчанию и одна запись в лог
    public <T> T guard(String what, Supplier<T> call, T fallback) {
        try {
            return call.get();
        } catch (RuntimeException | LinkageError e) {
            if (reported.add(what)) {
                LOGGER.error("{}: ошибка в аддоне ({})", AFriends.NAME, what, e);
            }
            return fallback;
        }
    }
}
