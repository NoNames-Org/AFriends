package dev.no.name.afriends.net.e2e;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.no.name.afriends.net.crypto.Bytes;
import dev.no.name.afriends.net.crypto.DhKey;
import dev.no.name.afriends.net.crypto.KemKey;
import dev.no.name.afriends.net.crypto.SignLabel;
import dev.no.name.afriends.net.identity.Identity;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;

// Свои предключи с секретными половинами: подписанный, постквантовый и одноразовые.
// Открытые половины выкладываются на сервер, по ним друзья ставят с нами сессию, пока нас нет в сети
public final class PrekeyRing {
    private static final long ROTATE_AFTER_MS = 7L * 86_400_000;
    public static final int ONE_TIME_BATCH = 50;
    public static final int ONE_TIME_LOW = 20;

    private record Signed(long id, DhKey key, long created) {
    }

    private record PostQuantum(long id, KemKey key) {
    }

    private final SecureRandom random = new SecureRandom();
    private final Map<Long, DhKey> oneTime = new LinkedHashMap<>();
    private Signed signed;
    private Signed previousSigned;
    private PostQuantum postQuantum;
    private PostQuantum previousPostQuantum;

    // пора ли сменить подписанный предключ (или его ещё нет)
    public boolean needsRotation(long now) {
        return signed == null || now - signed.created() > ROTATE_AFTER_MS;
    }

    // новый подписанный предключ; прежний остаётся до следующей смены для сообщений, которые уже в пути
    public void rotate(long now) throws GeneralSecurityException {
        previousSigned = signed;
        signed = new Signed(freshId(), DhKey.generate(), now);
        previousPostQuantum = postQuantum;
        postQuantum = KemKey.available() ? new PostQuantum(freshId(), KemKey.generate()) : null;
    }

    // тело keys.publish: текущие подписанные ключи и новая пачка одноразовых
    public JsonObject publishPayload(Identity identity, int newOneTime) {
        JsonObject payload = new JsonObject();
        JsonObject signedJson = new JsonObject();
        signedJson.addProperty("id", signed.id());
        signedJson.addProperty("pub", Bytes.base64(signed.key().publicRaw()));
        signedJson.addProperty("sig", Bytes.base64(identity.sign(SignLabel.SIGNED_PREKEY, signed.key().publicRaw())));
        payload.add("signed", signedJson);
        if (postQuantum != null) {
            JsonObject pq = new JsonObject();
            pq.addProperty("id", postQuantum.id());
            pq.addProperty("pub", Bytes.base64(postQuantum.key().publicRaw()));
            pq.addProperty("sig", Bytes.base64(identity.sign(SignLabel.PQ_PREKEY, postQuantum.key().publicRaw())));
            payload.add("pq", pq);
        }
        JsonArray batch = new JsonArray();
        for (int i = 0; i < newOneTime; i++) {
            long id = freshId();
            DhKey key = DhKey.generate();
            oneTime.put(id, key);
            JsonObject entry = new JsonObject();
            entry.addProperty("id", id);
            entry.addProperty("pub", Bytes.base64(key.publicRaw()));
            batch.add(entry);
        }
        payload.add("oneTime", batch);
        return payload;
    }

    // null — такого ключа у нас нет (слишком старый)
    DhKey signed(long id) {
        if (signed != null && signed.id() == id) {
            return signed.key();
        }
        return previousSigned != null && previousSigned.id() == id ? previousSigned.key() : null;
    }

    KemKey postQuantum(long id) {
        if (postQuantum != null && postQuantum.id() == id) {
            return postQuantum.key();
        }
        return previousPostQuantum != null && previousPostQuantum.id() == id ? previousPostQuantum.key() : null;
    }

    DhKey oneTime(long id) {
        return oneTime.get(id);
    }

    // одноразовый ключ стирается только после того, как первое сообщение расшифровалось:
    // иначе подделанным конвертом можно было бы сжечь ключ и сорвать настоящее сообщение
    void consumeOneTime(long id) {
        oneTime.remove(id);
    }

    private long freshId() {
        return random.nextInt() & 0x7FFF_FFFFL;
    }

    // ---- хранение ----

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (signed != null) {
            json.add("signed", signedJson(signed));
        }
        if (previousSigned != null) {
            json.add("previousSigned", signedJson(previousSigned));
        }
        if (postQuantum != null) {
            json.add("postQuantum", pqJson(postQuantum));
        }
        if (previousPostQuantum != null) {
            json.add("previousPostQuantum", pqJson(previousPostQuantum));
        }
        JsonArray keys = new JsonArray();
        oneTime.forEach((id, key) -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", id);
            entry.addProperty("secret", Bytes.base64(key.encodedSecret()));
            entry.addProperty("pub", Bytes.base64(key.publicRaw()));
            keys.add(entry);
        });
        json.add("oneTime", keys);
        return json;
    }

    public static PrekeyRing fromJson(JsonObject json) throws GeneralSecurityException {
        PrekeyRing ring = new PrekeyRing();
        ring.signed = signedFrom(json.getAsJsonObject("signed"));
        ring.previousSigned = signedFrom(json.getAsJsonObject("previousSigned"));
        // ключ, созданный на Java 24+, на Java 21 не восстановить: без него просто нет постквантовой части
        if (KemKey.available()) {
            ring.postQuantum = pqFrom(json.getAsJsonObject("postQuantum"));
            ring.previousPostQuantum = pqFrom(json.getAsJsonObject("previousPostQuantum"));
        }
        if (json.has("oneTime")) {
            for (JsonElement element : json.getAsJsonArray("oneTime")) {
                JsonObject entry = element.getAsJsonObject();
                ring.oneTime.put(entry.get("id").getAsLong(), dh(entry));
            }
        }
        return ring;
    }

    private static JsonObject signedJson(Signed value) {
        JsonObject json = new JsonObject();
        json.addProperty("id", value.id());
        json.addProperty("created", value.created());
        json.addProperty("secret", Bytes.base64(value.key().encodedSecret()));
        json.addProperty("pub", Bytes.base64(value.key().publicRaw()));
        return json;
    }

    private static JsonObject pqJson(PostQuantum value) {
        JsonObject json = new JsonObject();
        json.addProperty("id", value.id());
        json.addProperty("secret", Bytes.base64(value.key().encodedSecret()));
        json.addProperty("pub", Bytes.base64(value.key().publicRaw()));
        return json;
    }

    private static Signed signedFrom(JsonObject json) throws GeneralSecurityException {
        return json == null ? null : new Signed(json.get("id").getAsLong(), dh(json), json.get("created").getAsLong());
    }

    private static PostQuantum pqFrom(JsonObject json) throws GeneralSecurityException {
        return json == null ? null : new PostQuantum(json.get("id").getAsLong(),
                KemKey.restore(Bytes.fromBase64(json.get("secret").getAsString()), Bytes.fromBase64(json.get("pub").getAsString())));
    }

    private static DhKey dh(JsonObject json) throws GeneralSecurityException {
        return DhKey.restore(Bytes.fromBase64(json.get("secret").getAsString()), Bytes.fromBase64(json.get("pub").getAsString()));
    }
}
