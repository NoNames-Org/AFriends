package dev.no.name.blooddesigner.net.game.friends;

import dev.no.name.blooddesigner.BloodDesigner;
import dev.no.name.blooddesigner.core.FriendManager;
import dev.no.name.blooddesigner.net.model.Connection;
import dev.no.name.blooddesigner.net.model.LinkFriend;
import dev.no.name.blooddesigner.net.model.LinkRequest;
import dev.no.name.blooddesigner.net.model.LinkSnapshot;
import dev.no.name.blooddesigner.net.service.LinkService;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

// Сведение двух списков: друзья по сети сами появляются в обычном списке сервера, где ты сейчас,
// а заявка от того, кто у тебя уже в друзьях, принимается без вопросов. Поток игры
public final class FriendSync {
    private static final Logger LOGGER = LoggerFactory.getLogger(BloodDesigner.NAME);
    // удалённого друга сеть ещё помнит до следующей синхронизации — не возвращаем его в список
    private static final long FORGET_MS = 120_000;

    private final LinkService link;
    private final FriendRemovals removals;
    private final Map<String, Long> removedAt = new ConcurrentHashMap<>();
    private final Map<String, Long> answered = new ConcurrentHashMap<>();
    // заявки, сделанные, пока сеть переподключалась: уйдут, как только связь появится
    private final Map<String, Consumer<String>> waiting = new LinkedHashMap<>();
    private boolean wasOnline;
    private LinkSnapshot merged;
    private String mergedScope;

    // removeEverywhere — убрать друга по нику из списков всех серверов (он удалил нас сам)
    public FriendSync(LinkService link, Consumer<String> removeEverywhere) {
        this.link = link;
        this.removals = new FriendRemovals(link::snapshot, this::removedByMe, removeEverywhere);
    }

    // раз в тик, на сервере и в меню: не удалил ли нас кто-то из друзей, не пора ли отправить отложенные заявки
    public void tick() {
        boolean online = active();
        if (online != wasOnline) {
            wasOnline = online;
            LOGGER.info("{}: сеть {}", BloodDesigner.NAME, online ? "подключена" : "отключена (" + link.snapshot().connection() + ")");
        }
        removals.tick();
        if (!waiting.isEmpty() && active()) {
            Map<String, Consumer<String>> ready = new LinkedHashMap<>(waiting);
            waiting.clear();
            ready.forEach(this::request);
        }
    }

    private boolean removedByMe(String name) {
        return System.currentTimeMillis() - removedAt.getOrDefault(key(name), 0L) <= FORGET_MS;
    }

    public boolean active() {
        return link.snapshot().connection() == Connection.ONLINE;
    }

    public LinkFriend friend(String name) {
        return link.snapshot().friendNamed(name);
    }

    public List<String> requests() {
        List<String> names = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (LinkRequest request : link.snapshot().incoming()) {
            if (now - answered.getOrDefault(key(request.name()), 0L) > FORGET_MS) {
                names.add(request.name());
            }
        }
        return names;
    }

    // друга добавили по нику: если у него есть мод, сеть позовёт его в друзья.
    // Связи сейчас нет — заявка не теряется, а ждёт подключения
    public void added(String name, Consumer<String> notice) {
        removedAt.remove(key(name));
        if (friend(name) != null) {
            return;
        }
        if (!active()) {
            LOGGER.info("{}: заявка {} ждёт подключения к сети", BloodDesigner.NAME, name);
            waiting.put(name, notice);
            return;
        }
        request(name, notice);
    }

    private void request(String name, Consumer<String> notice) {
        link.requestFriend(name).thenAccept(code -> {
            LOGGER.info("{}: заявка в друзья {} — {}", BloodDesigner.NAME, name, code);
            // not_found — у него нет мода, это обычный друг, молчим
            String text = switch (code) {
                case "ok" -> "У " + name + " тоже BloodDesigner — заявка ушла в сеть";
                case "not_found", "already_friends" -> null;
                case "limit" -> "Заявка в сеть не ушла: слишком много друзей или заявок";
                default -> "Заявка в сеть не ушла (" + code + "), попробуй ещё раз";
            };
            if (text != null) {
                Minecraft.getInstance().execute(() -> notice.accept(text));
            }
        });
    }

    public void removed(String name) {
        removedAt.put(key(name), System.currentTimeMillis());
        LinkFriend friend = friend(name);
        if (friend != null) {
            link.removeFriend(friend.id()).thenAccept(code ->
                    LOGGER.info("{}: удаление из друзей {} — {}", BloodDesigner.NAME, name, code));
        }
        waiting.remove(name);
    }

    public void respond(String name, boolean accept) {
        for (LinkRequest request : link.snapshot().incoming()) {
            if (request.name().equalsIgnoreCase(name)) {
                answered.put(key(name), System.currentTimeMillis());
                link.respond(request.id(), accept).thenAccept(code ->
                        LOGGER.info("{}: ответ на заявку {} ({}) — {}", BloodDesigner.NAME, name, accept ? "да" : "нет", code));
            }
        }
    }

    // раз в тик на сервере; scope — ключ сервера: у каждого сервера свой список друзей
    public void merge(FriendManager friends, String scope) {
        LinkSnapshot state = link.snapshot();
        if (state == merged && scope.equals(mergedScope)) {
            return;
        }
        merged = state;
        mergedScope = scope;
        if (state.connection() != Connection.ONLINE) {
            return;
        }
        long now = System.currentTimeMillis();
        for (LinkFriend friend : state.friends()) {
            if (friends.find(friend.name()) == null && now - removedAt.getOrDefault(key(friend.name()), 0L) > FORGET_MS) {
                friends.add(friend.name());
            }
        }
        for (LinkRequest request : state.incoming()) {
            if (friends.find(request.name()) != null && now - answered.getOrDefault(key(request.name()), 0L) > FORGET_MS) {
                respond(request.name(), true);
            }
        }
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
