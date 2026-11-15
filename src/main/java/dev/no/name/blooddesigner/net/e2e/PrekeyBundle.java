package dev.no.name.blooddesigner.net.e2e;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.no.name.blooddesigner.net.crypto.Bytes;
import dev.no.name.blooddesigner.net.crypto.DhKey;
import dev.no.name.blooddesigner.net.crypto.KemKey;
import dev.no.name.blooddesigner.net.crypto.SignKey;
import dev.no.name.blooddesigner.net.crypto.SignLabel;

// Связка ключей друга с сервера (ответ keys.fetch), нужна чтобы написать ему первым.
// Создаётся только через parse, который проверяет все подписи
public record PrekeyBundle(FriendKeys keys, long signedId, byte[] signedKey, long oneTimeId, byte[] oneTimeKey,
                           long postQuantumId, byte[] postQuantumKey) {
    // «ключа нет» в полях-идентификаторах
    public static final long NONE = 0xFFFF_FFFFL;

    public boolean hasOneTime() {
        return oneTimeKey != null;
    }

    public boolean hasPostQuantum() {
        return postQuantumKey != null;
    }

    public static PrekeyBundle parse(JsonObject json, String expectedId) throws E2eException {
        try {
            byte[] signKey = bytes(json, "signKey");
            FriendKeys keys = FriendKeys.verified(expectedId, signKey, bytes(json, "boxKey"), bytes(json, "keySig"));

            JsonObject signed = json.getAsJsonObject("signed");
            byte[] signedKey = bytes(signed, "pub");
            if (signedKey.length != DhKey.KEY_BYTES || !SignKey.verify(signKey, SignLabel.SIGNED_PREKEY, signedKey, bytes(signed, "sig"))) {
                throw new E2eException("bad_signed_prekey");
            }

            long oneTimeId = NONE;
            byte[] oneTimeKey = null;
            if (present(json, "oneTime")) {
                JsonObject oneTime = json.getAsJsonObject("oneTime");
                oneTimeId = id(oneTime);
                oneTimeKey = bytes(oneTime, "pub");
                if (oneTimeKey.length != DhKey.KEY_BYTES) {
                    throw new E2eException("bad_one_time_prekey");
                }
            }

            long postQuantumId = NONE;
            byte[] postQuantumKey = null;
            if (present(json, "pq")) {
                JsonObject pq = json.getAsJsonObject("pq");
                postQuantumId = id(pq);
                postQuantumKey = bytes(pq, "pub");
                if (postQuantumKey.length != KemKey.PUBLIC_BYTES || !SignKey.verify(signKey, SignLabel.PQ_PREKEY, postQuantumKey, bytes(pq, "sig"))) {
                    throw new E2eException("bad_pq_prekey");
                }
            }
            return new PrekeyBundle(keys, id(signed), signedKey, oneTimeId, oneTimeKey, postQuantumId, postQuantumKey);
        } catch (RuntimeException e) {
            // не тот тип поля, нет поля, битый base64
            throw new E2eException("bad_bundle", e);
        }
    }

    private static boolean present(JsonObject json, String field) {
        JsonElement value = json.get(field);
        return value != null && value.isJsonObject();
    }

    private static long id(JsonObject json) throws E2eException {
        long value = json.get("id").getAsLong();
        if (value < 0 || value >= NONE) {
            throw new E2eException("bad_bundle");
        }
        return value;
    }

    private static byte[] bytes(JsonObject json, String field) {
        return Bytes.fromBase64(json.get(field).getAsString());
    }
}
