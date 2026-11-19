package dev.no.name.afriends.addon;

import dev.no.name.afriends.core.FriendEvents;
import dev.no.name.afriends.AFriends;
import dev.no.name.afriends.api.ComposeAction;
import dev.no.name.afriends.api.FriendAction;
import dev.no.name.afriends.api.FriendListener;
import dev.no.name.afriends.api.FriendRequests;
import dev.no.name.afriends.api.FriendStatus;
import dev.no.name.afriends.api.MessageCard;
import dev.no.name.afriends.api.Privacy;
import dev.no.name.afriends.api.ProfileStatus;
import dev.no.name.afriends.api.RailPanel;
import dev.no.name.afriends.api.SendCheck;
import dev.no.name.afriends.api.SettingsCategory;
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

// Всё, что встроили аддоны других модов. Окна AFriends спрашивают тут;
// вызов кода аддона идёт через guard: ошибка пишется в лог один раз и не роняет игру
public final class AddonRegistry implements SendPolicy, FriendEvents {
    private static final Logger LOGGER = LoggerFactory.getLogger(AFriends.NAME);

    public record Request(String name, String hint, FriendRequests owner) {
    }

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

    // ---- регистрация (через AddonApi) ----

    public void addRailPanel(RailPanel panel) {
        railPanels.add(panel);
    }

    public void addSettingsCategory(SettingsCategory category) {
        settingsCategories.add(category);
    }

    public void addSendCheck(SendCheck check) {
        sendChecks.add(check);
    }

    public void setPrivacy(Privacy value) {
        privacy = value;
    }

    public void addFriendStatus(FriendStatus status) {
        statuses.add(status);
    }

    public void addFriendAction(FriendAction action) {
        actions.add(action);
    }

    public void addFriendListener(FriendListener listener) {
        listeners.add(listener);
    }

    public void addFriendRequests(FriendRequests value) {
        requests.add(value);
    }

    public void setProfileStatus(ProfileStatus status) {
        profile = status;
    }

    public void addMessageCard(MessageCard card) {
        cards.add(card);
    }

    public void addComposeAction(ComposeAction action) {
        composeActions.add(action);
    }

    // ---- для окон AFriends ----

    public List<RailPanel> railPanels() {
        return railPanels;
    }

    public List<SettingsCategory> settingsCategories() {
        return settingsCategories;
    }

    @Override
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

    @Override
    public void added(String name, Consumer<String> notice) {
        listeners.forEach(listener -> run("friendListener", () -> listener.added(name, notice)));
    }

    @Override
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
