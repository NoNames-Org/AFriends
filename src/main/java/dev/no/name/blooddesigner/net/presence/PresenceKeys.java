package dev.no.name.blooddesigner.net.presence;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.no.name.blooddesigner.net.crypto.Aead;
import dev.no.name.blooddesigner.net.crypto.Bytes;
import dev.no.name.blooddesigner.net.e2e.SessionStore;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Ключи статусов (CRYPTO.md, раздел 7а): свой текущий, кому из друзей он уже доставлен, и ключи друзей.
// Не потокобезопасен: живёт на потоке сети, как и сессии
public final class PresenceKeys {
    private static final String RECORD = "presence";
    private static final long ROTATE_AFTER_MS = 7L * 86_400_000;
    // ключей друга храним два: статус, закрытый перед сменой ключа, ещё читается
    private static final int KEPT_PER_FRIEND = 2;

    // ключ с номером эпохи
    public record Key(long epoch, byte[] secret) {
    }

    private final SessionStore store;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Long> delivered = new HashMap<>();
    private final Map<String, List<Key>> friends = new HashMap<>();
    private Key own;
    private long ownCreated;

    public PresenceKeys(SessionStore store) {
        this.store = store;
        load(store.load(RECORD));
    }

    // свой ключ; старый неделю — меняется сам
    public Key own(long now) {
        if (own == null || now - ownCreated > ROTATE_AFTER_MS) {
            rotate(now);
        }
        return own;
    }

    // новый ключ: после удаления друга, чтобы бывший друг не читал новые статусы
    public void rotate(long now) {
        byte[] secret = new byte[Aead.KEY_BYTES];
        random.nextBytes(secret);
        own = new Key(own == null ? 1 : own.epoch() + 1, secret);
        ownCreated = now;
        save();
    }

    public boolean delivered(String friendId, long epoch) {
        return delivered.getOrDefault(friendId, 0L) == epoch;
    }

    public void markDelivered(String friendId, long epoch) {
        delivered.put(friendId, epoch);
        save();
    }

    // ключ друга пришёл сквозным сообщением. Старые эпохи не принимаем: повтор не откатит ключ назад
    public boolean remember(String friendId, long epoch, byte[] secret) {
        if (secret.length != Aead.KEY_BYTES) {
            return false;
        }
        List<Key> keys = friends.computeIfAbsent(friendId, id -> new ArrayList<>());
        if (keys.stream().anyMatch(key -> key.epoch() >= epoch)) {
            return false;
        }
        keys.add(new Key(epoch, secret.clone()));
        keys.sort(Comparator.comparingLong(Key::epoch).reversed());
        while (keys.size() > KEPT_PER_FRIEND) {
            Bytes.wipe(keys.remove(keys.size() - 1).secret());
        }
        save();
        return true;
    }

    // null — такой эпохи у нас нет
    public byte[] friendKey(String friendId, long epoch) {
        for (Key key : friends.getOrDefault(friendId, List.of())) {
            if (key.epoch() == epoch) {
                return key.secret();
            }
        }
        return null;
    }

    // бывший друг: его ключи и отметки о доставке больше не нужны
    public void forget(String friendId) {
        delivered.remove(friendId);
        List<Key> keys = friends.remove(friendId);
        if (keys != null) {
            keys.forEach(key -> Bytes.wipe(key.secret()));
        }
        save();
    }

    // ---- хранение ----

    private void load(JsonObject json) {
        if (json == null) {
            return;
        }
        try {
            if (json.has("own")) {
                JsonObject ownJson = json.getAsJsonObject("own");
                own = new Key(ownJson.get("e").getAsLong(), Bytes.fromBase64(ownJson.get("key").getAsString()));
                ownCreated = ownJson.get("created").getAsLong();
            }
            json.getAsJsonObject("delivered").entrySet().forEach(entry -> delivered.put(entry.getKey(), entry.getValue().getAsLong()));
            json.getAsJsonObject("friends").entrySet().forEach(entry -> {
                List<Key> keys = new ArrayList<>();
                for (JsonElement element : entry.getValue().getAsJsonArray()) {
                    JsonObject key = element.getAsJsonObject();
                    keys.add(new Key(key.get("e").getAsLong(), Bytes.fromBase64(key.get("key").getAsString())));
                }
                friends.put(entry.getKey(), keys);
            });
        } catch (RuntimeException e) {
            // битая запись: начнём заново, друзья получат новый ключ
            own = null;
            delivered.clear();
            friends.clear();
        }
    }

    private void save() {
        JsonObject json = new JsonObject();
        if (own != null) {
            JsonObject ownJson = new JsonObject();
            ownJson.addProperty("e", own.epoch());
            ownJson.addProperty("key", Bytes.base64(own.secret()));
            ownJson.addProperty("created", ownCreated);
            json.add("own", ownJson);
        }
        JsonObject deliveredJson = new JsonObject();
        delivered.forEach(deliveredJson::addProperty);
        json.add("delivered", deliveredJson);
        JsonObject friendsJson = new JsonObject();
        friends.forEach((id, keys) -> {
            JsonArray array = new JsonArray();
            for (Key key : keys) {
                JsonObject keyJson = new JsonObject();
                keyJson.addProperty("e", key.epoch());
                keyJson.addProperty("key", Bytes.base64(key.secret()));
                array.add(keyJson);
            }
            friendsJson.add(id, array);
        });
        json.add("friends", friendsJson);
        store.save(RECORD, json);
    }
}
