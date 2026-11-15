package dev.no.name.blooddesigner.addon;

import dev.no.name.blooddesigner.core.modes.BloodstoneServer;
import dev.no.name.blooddesigner.BloodDesigner;
import dev.no.name.blooddesigner.api.BloodDesignerAddon;
import dev.no.name.blooddesigner.api.BloodDesignerApi;
import dev.no.name.blooddesigner.api.ComposeAction;
import dev.no.name.blooddesigner.api.FriendAction;
import dev.no.name.blooddesigner.api.FriendListener;
import dev.no.name.blooddesigner.api.FriendRequests;
import dev.no.name.blooddesigner.api.FriendStatus;
import dev.no.name.blooddesigner.api.Friends;
import dev.no.name.blooddesigner.api.MessageCard;
import dev.no.name.blooddesigner.api.Privacy;
import dev.no.name.blooddesigner.api.ProfileStatus;
import dev.no.name.blooddesigner.api.SendCheck;
import dev.no.name.blooddesigner.core.FriendManager;
import dev.no.name.blooddesigner.core.MessageRoute;
import dev.no.name.blooddesigner.core.ServerScope;
import dev.no.name.blooddesigner.core.SocialData;
import dev.no.name.blooddesigner.core.party.PartyService;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

// Дополнения других модов и всё, что они встроили. Окна BloodDesigner спрашивают тут;
// ошибка в дополнении пишется в лог один раз и не роняет игру
public final class Addons implements BloodDesignerApi {
    private static final Logger LOGGER = LoggerFactory.getLogger(BloodDesigner.NAME);
    private static final String ENTRYPOINT = BloodDesigner.MOD_ID;
    private static Addons instance = new Addons(null, null, () -> { }, null);

    public record Request(String name, String hint, FriendRequests owner) {
    }

    private final SocialData social;
    private final PartyService party;
    private final Runnable save;
    private final MessageRoute route;
    private final List<SendCheck> sendChecks = new CopyOnWriteArrayList<>();
    private final List<FriendStatus> statuses = new CopyOnWriteArrayList<>();
    private final List<FriendAction> actions = new CopyOnWriteArrayList<>();
    private final List<FriendListener> listeners = new CopyOnWriteArrayList<>();
    private final List<FriendRequests> requests = new CopyOnWriteArrayList<>();
    private final List<MessageCard> cards = new CopyOnWriteArrayList<>();
    private final List<ComposeAction> composeActions = new CopyOnWriteArrayList<>();
    private final Set<String> reported = new HashSet<>();
    private volatile Privacy privacy;
    private volatile ProfileStatus profile;

    private Addons(SocialData social, PartyService party, Runnable save, MessageRoute route) {
        this.social = social;
        this.party = party;
        this.save = save;
        this.route = route;
    }

    // при запуске: свои встроенные части (сеть) первыми, потом дополнения других модов
    public static void load(SocialData social, PartyService party, Runnable save, MessageRoute route,
                            List<BloodDesignerAddon> builtIn) {
        Addons addons = new Addons(social, party, save, route);
        instance = addons;
        builtIn.forEach(addon -> addon.register(addons));
        for (EntrypointContainer<BloodDesignerAddon> container
                : FabricLoader.getInstance().getEntrypointContainers(ENTRYPOINT, BloodDesignerAddon.class)) {
            String mod = container.getProvider().getMetadata().getId();
            try {
                container.getEntrypoint().register(addons);
                LOGGER.info("{}: подключено дополнение {}", BloodDesigner.NAME, mod);
            } catch (RuntimeException | LinkageError e) {
                LOGGER.error("{}: дополнение {} не подключилось", BloodDesigner.NAME, mod, e);
            }
        }
    }

    public static Addons get() {
        return instance;
    }

    // ---- для дополнений ----

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
        };
    }

    @Override
    public void inviteToParty(String name) {
        if (party != null && BloodstoneServer.isConnected()) {
            party.invite(name);
        }
    }

    @Override
    public Optional<String> sendMessage(String partner, String text) {
        if (route == null) {
            return Optional.of("BloodDesigner ещё не готов");
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

    // ---- для окон BloodDesigner ----

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
        } catch (RuntimeException e) {
            if (reported.add(what)) {
                LOGGER.error("{}: ошибка в дополнении ({})", BloodDesigner.NAME, what, e);
            }
            return fallback;
        }
    }
}
