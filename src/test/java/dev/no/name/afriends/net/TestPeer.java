package dev.no.name.afriends.net;

import com.google.gson.JsonObject;
import dev.no.name.afriends.net.crypto.Bytes;
import dev.no.name.afriends.net.e2e.E2eSessions;
import dev.no.name.afriends.net.e2e.FriendKeys;
import dev.no.name.afriends.net.e2e.SessionStore;
import dev.no.name.afriends.net.identity.Identity;

import java.util.HashMap;
import java.util.Map;

// один участник переписки для проверок: личность, сессии и хранилище в памяти
final class TestPeer {
    static final class MemoryStore implements SessionStore {
        private final Map<String, String> records = new HashMap<>();

        @Override
        public JsonObject load(String name) {
            String saved = records.get(name);
            return saved == null ? null : com.google.gson.JsonParser.parseString(saved).getAsJsonObject();
        }

        @Override
        public void save(String name, JsonObject state) {
            records.put(name, state.toString());
        }

        @Override
        public void delete(String name) {
            records.remove(name);
        }
    }

    final Identity identity = Identity.generate();
    final MemoryStore store = new MemoryStore();
    E2eSessions sessions;

    TestPeer() throws Exception {
        sessions = new E2eSessions(identity, store);
        sessions.ring().rotate(System.currentTimeMillis());
    }

    // как после перезапуска игры: всё состояние читается из хранилища заново
    void restart() throws Exception {
        sessions = new E2eSessions(identity, store);
    }

    FriendKeys keys() throws Exception {
        return FriendKeys.verified(identity.id(), identity.signPublic(), identity.exchangePublic(), identity.keyCardSignature());
    }

    // то, что сервер отдал бы на keys.fetch: карточка ключей, подписанные предключи и один одноразовый
    JsonObject bundle(boolean withOneTime) {
        JsonObject published = sessions.ring().publishPayload(identity, withOneTime ? 1 : 0);
        sessions.saveRing();
        JsonObject bundle = new JsonObject();
        bundle.addProperty("signKey", Bytes.base64(identity.signPublic()));
        bundle.addProperty("boxKey", Bytes.base64(identity.exchangePublic()));
        bundle.addProperty("keySig", Bytes.base64(identity.keyCardSignature()));
        bundle.add("signed", published.get("signed"));
        if (published.has("pq")) {
            bundle.add("pq", published.get("pq"));
        }
        if (withOneTime) {
            bundle.add("oneTime", published.getAsJsonArray("oneTime").get(0));
        }
        return bundle;
    }
}
