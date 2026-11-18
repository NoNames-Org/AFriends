package dev.no.name.afriends.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private Runnable onMessagesChange = () -> { };
    private Predicate<String> staysArchived = chat -> false;

    public SocialData() {
        this(AccountScope::current, ServerScope::current);
    }

    // account и server — откуда брать текущий аккаунт и сервер (в тестах — свои)
    public SocialData(Supplier<String> account, Supplier<String> server) {
        this.account = account;
        this.server = server;
    }

    public void setOnMessagesChange(Runnable listener) {
        onMessagesChange = listener;
        everyScope().forEach(scope -> scope.messages().setOnChange(listener));
    }

    // какие чаты не возвращаются из архива новым сообщением (заглушённые)
    public void setStaysArchived(Predicate<String> rule) {
        staysArchived = rule;
        everyScope().forEach(scope -> scope.messages().setStaysArchived(rule));
    }

    public void trimMessages() {
        everyScope().forEach(scope -> scope.messages().trimAll());
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

    // реакция по сети: сообщение с этим id ищем на всех серверах аккаунта — реакция может прийти и из главного меню
    public boolean react(String partner, String messageId, boolean mine, String reaction) {
        for (PrivateMessages messages : accountMessages()) {
            if (messages.react(partner, messageId, mine, reaction)) {
                return true;
            }
        }
        return false;
    }

    // голос в опросе группы по сети: ищем на всех серверах аккаунта, как реакцию. voter null — свой
    public boolean vote(String chat, String messageId, String voter, int choice) {
        for (PrivateMessages messages : accountMessages()) {
            if (messages.vote(chat, messageId, voter, choice)) {
                return true;
            }
        }
        return false;
    }

    // собеседник удалил сообщения у обоих: ищем на всех серверах аккаунта, как реакцию
    // чат пропадает на всех серверах этого аккаунта (вышел из группы)
    public void deleteChatEverywhere(String chat) {
        accountMessages().forEach(messages -> messages.delete(chat));
    }

    public void removeEverywhere(String partner, Collection<String> messageIds) {
        accountMessages().forEach(messages -> messages.remove(partner, messageIds));
    }

    // отметка на своё сообщение: оно могло уйти с любого сервера этого аккаунта
    public void receiptEverywhere(List<String> messageIds, int state) {
        accountMessages().forEach(messages -> messageIds.forEach(id -> messages.markReceipt(id, state)));
    }

    // собеседник закрепил или открепил сообщение у обоих
    public void pinEverywhere(String partner, String messageId, boolean pinned) {
        accountMessages().forEach(messages -> messages.pin(partner, messageId, pinned));
    }

    // друг удалил нас в сети: на каждом сервере этого аккаунта он больше не друг. true — где-то был
    public boolean removeFriendEverywhere(String name) {
        boolean removed = false;
        for (Scope scope : accountScopes()) {
            removed |= scope.friends().remove(name);
        }
        return removed;
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

    private List<PrivateMessages> accountMessages() {
        return accountScopes().stream().map(Scope::messages).toList();
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
        onMessagesChange.run();
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
            onMessagesChange.run();
        }
        scopes.put(key, created);
        return created;
    }

    private Scope newScope() {
        Scope scope = new Scope(new FriendManager(), new IgnoreList(), new PrivateMessages());
        scope.messages().setOnChange(onMessagesChange);
        scope.messages().setStaysArchived(staysArchived);
        return scope;
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
