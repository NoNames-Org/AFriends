package dev.no.name.afriends.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Друзья, игнор и переписки — свои у каждого аккаунта Minecraft и у каждого сервера. Ключ — «аккаунт|сервер».
 * Данные версий без аккаунтов (ключ — просто сервер) достаются первому аккаунту, под которым зайдут после обновления.
 * Сетевые события (реакции, голоса, удаления) ищут сообщения только среди серверов текущего аккаунта
 */
public final class SocialData {
    private static final String LEGACY_KEY = "$unassigned";
    private static final char SEPARATOR = '|';

    public record Scope(FriendManager friends, IgnoreList ignored, PrivateMessages messages) {
        boolean isEmpty() {
            return friends.size() == 0 && ignored.all().isEmpty() && messages.isEmpty();
        }
    }

    private final Supplier<String> account;
    private final Supplier<String> server;
    private final Map<String, Scope> scopes = new LinkedHashMap<>();
    // из версий без аккаунтов: сервер → данные; достанутся первому аккаунту
    private final Map<String, Scope> unowned = new LinkedHashMap<>();

    private Scope legacy;
    private final SkinMemory skins;
    // какие чаты не возвращаются из архива новым сообщением (заглушённые)
    private final Predicate<String> staysArchived;
    private final List<MessageEvents> listeners = new CopyOnWriteArrayList<>();
    private final AccountWide accountWide = new AccountWide(this::accountScopes);
    // общий для всех scope: переписка любого аккаунта и сервера сообщает подписчикам SocialData
    private final MessageEvents events = new MessageEvents() {
        @Override
        public void changed() {
            listeners.forEach(MessageEvents::changed);
        }

        @Override
        public void incoming(String chat) {
            listeners.forEach(listener -> listener.incoming(chat));
        }
    };

    public SocialData(SkinMemory skins, Predicate<String> staysArchived) {
        this(AccountScope::current, ServerScope::current, skins, staysArchived);
    }

    // account и server — откуда брать текущий аккаунт и сервер (в тестах — свои)
    public SocialData(Supplier<String> account, Supplier<String> server) {
        this(account, server, SkinMemory.NONE, chat -> false);
    }

    public SocialData(Supplier<String> account, Supplier<String> server, SkinMemory skins, Predicate<String> staysArchived) {
        this.account = account;
        this.server = server;
        this.skins = skins;
        this.staysArchived = staysArchived;
    }

    public void addListener(MessageEvents listener) {
        listeners.add(listener);
    }

    public Scope current() {
        String owner = account.get();
        adopt(owner);
        return scope(owner + SEPARATOR + server.get());
    }

    public FriendManager friends() {
        return current().friends();
    }

    public IgnoreList ignored() {
        return current().ignored();
    }

    public PrivateMessages messages() {
        return current().messages();
    }

    /** Изменения из сети, которые касаются всех серверов текущего аккаунта. */
    public AccountWide accountWide() {
        return accountWide;
    }

    public void tick() {
        if (!ServerScope.NONE.equals(server.get())) {
            friends().tick();
        }
    }

    // сервера текущего аккаунта (и ещё не разобранные старые данные)
    private List<Scope> accountScopes() {
        String owner = account.get();
        adopt(owner);
        String prefix = owner + SEPARATOR;
        List<Scope> list = new ArrayList<>();
        scopes.forEach((key, scope) -> {
            if (key.startsWith(prefix)) {
                list.add(scope);
            }
        });
        if (legacy != null) {
            list.add(legacy);
        }
        return list;
    }

    private List<Scope> everyScope() {
        List<Scope> all = new ArrayList<>(scopes.values());
        all.addAll(unowned.values());
        if (legacy != null) {
            all.add(legacy);
        }
        return all;
    }

    // старые данные без аккаунта переходят первому аккаунту; конфиг сохранится с новыми ключами
    private void adopt(String owner) {
        if (unowned.isEmpty()) {
            return;
        }
        unowned.forEach((serverKey, scope) -> scopes.putIfAbsent(owner + SEPARATOR + serverKey, scope));
        unowned.clear();
        events.changed();
    }

    private Scope scope(String key) {
        Scope existing = scopes.get(key);
        if (existing != null) {
            return existing;
        }
        boolean menu = key.endsWith(SEPARATOR + ServerScope.NONE);
        Scope created = legacy != null && !menu ? legacy : newScope();
        if (created == legacy) {
            legacy = null;
            events.changed();
        }
        scopes.put(key, created);
        return created;
    }

    private Scope newScope() {
        return new Scope(new FriendManager(skins), new IgnoreList(), new PrivateMessages(events, staysArchived));
    }

    private Scope legacyScope() {
        if (legacy == null) {
            legacy = newScope();
        }
        return legacy;
    }

    public JsonElement friendsJson() {
        return write(scope -> scope.friends().toJson());
    }

    public void friendsFromJson(JsonElement json) {
        read(json, JsonElement::isJsonArray, (scope, data) -> scope.friends().fromJson(data));
    }

    public JsonElement ignoredJson() {
        return write(scope -> scope.ignored().toJson());
    }

    public void ignoredFromJson(JsonElement json) {
        read(json, JsonElement::isJsonArray, (scope, data) -> scope.ignored().fromJson(data));
    }

    public JsonElement messagesJson() {
        return write(scope -> scope.messages().toJson());
    }

    public void messagesFromJson(JsonElement json) {
        read(json, SocialData::isLegacyMessages, (scope, data) -> scope.messages().fromJson(data));
    }

    private static boolean isLegacyMessages(JsonElement json) {
        if (!json.isJsonObject()) {
            return false;
        }
        return json.getAsJsonObject().entrySet().stream().anyMatch(entry -> entry.getValue().isJsonArray());
    }

    private JsonElement write(Function<Scope, JsonElement> writer) {
        JsonObject json = new JsonObject();
        scopes.forEach((key, scope) -> {
            if (!key.endsWith(SEPARATOR + ServerScope.NONE) && !scope.isEmpty()) {
                json.add(key, writer.apply(scope));
            }
        });
        unowned.forEach((key, scope) -> {
            if (!scope.isEmpty()) {
                json.add(key, writer.apply(scope));
            }
        });
        if (legacy != null && !legacy.isEmpty()) {
            json.add(LEGACY_KEY, writer.apply(legacy));
        }
        return json;
    }

    private void read(JsonElement json, Predicate<JsonElement> isLegacy, BiConsumer<Scope, JsonElement> reader) {
        if (isLegacy.test(json)) {
            reader.accept(legacyScope(), json);
            return;
        }
        if (!json.isJsonObject()) {
            return;
        }
        json.getAsJsonObject().entrySet().forEach(entry -> {
            String key = entry.getKey();
            Scope scope;
            if (LEGACY_KEY.equals(key)) {
                scope = legacyScope();
            } else if (key.indexOf(SEPARATOR) >= 0) {
                scope = scopes.computeIfAbsent(key, ignored -> newScope());
            } else {
                scope = unowned.computeIfAbsent(key, ignored -> newScope());
            }
            reader.accept(scope, entry.getValue());
        });
    }
}
