package dev.no.name.blooddesigner.net.e2e;

import com.google.gson.JsonObject;
import dev.no.name.blooddesigner.net.crypto.Bytes;

import java.security.GeneralSecurityException;
import java.util.Arrays;

// Сессия с одним другом. Обычно в ней один храповик. Второй (запасной) появляется, когда оба
// написали друг другу первыми одновременно или друг пересоздал сессию: им дочитываются сообщения,
// зашифрованные по старой или встречной сессии
final class Session {
    // один храповик вместе с тем, что нужно для его работы
    private static final class Leg {
        final Ratchet ratchet;
        final byte[] associated;
        // одноразовый ключ инициатора, с которого началась эта ветка: по нему узнаём повторное «первое» сообщение
        final byte[] ephemeral;
        final boolean mine;

        Leg(Ratchet ratchet, byte[] associated, byte[] ephemeral, boolean mine) {
            this.ratchet = ratchet;
            this.associated = associated;
            this.ephemeral = ephemeral;
            this.mine = mine;
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.add("ratchet", ratchet.toJson());
            json.addProperty("associated", Bytes.base64(associated));
            json.addProperty("ephemeral", Bytes.base64(ephemeral));
            json.addProperty("mine", mine);
            return json;
        }

        static Leg fromJson(JsonObject json) throws GeneralSecurityException {
            return json == null ? null : new Leg(Ratchet.fromJson(json.getAsJsonObject("ratchet")),
                    Bytes.fromBase64(json.get("associated").getAsString()), Bytes.fromBase64(json.get("ephemeral").getAsString()),
                    json.get("mine").getAsBoolean());
        }
    }

    private Leg current;
    private Leg fallback;
    // пока друг ни разу не ответил, к каждому сообщению прикладывается предложение сессии
    private Offer pending;

    private Session() {
    }

    // я начал сессию
    static Session started(Ratchet ratchet, byte[] associated, Offer offer) {
        Session session = new Session();
        session.current = new Leg(ratchet, associated, offer.ephemeral(), true);
        session.pending = offer;
        return session;
    }

    byte[] seal(byte[] padded) throws E2eException {
        return new Envelope(pending, current.ratchet.encrypt(padded, current.associated)).encode();
    }

    // есть ли ветка, начатая этим предложением (друг повторяет «первое» сообщение, пока не получил ответ)
    boolean knows(Offer offer) {
        return leg(offer.ephemeral()) != null;
    }

    byte[] openRepeated(Offer offer, byte[] message, long now) throws E2eException {
        return leg(offer.ephemeral()).ratchet.decrypt(message, leg(offer.ephemeral()).associated, now);
    }

    byte[] open(byte[] message, long now) throws E2eException {
        try {
            byte[] plaintext = current.ratchet.decrypt(message, current.associated, now);
            // друг ответил в нашей сессии: он её принял, предложение больше не нужно
            if (current.mine) {
                pending = null;
            }
            return plaintext;
        } catch (E2eException e) {
            if (fallback == null) {
                throw e;
            }
            return fallback.ratchet.decrypt(message, fallback.associated, now);
        }
    }

    // Друг начал свою сессию, и её первое сообщение уже расшифровано. Если мы оба начали одновременно,
    // остаётся сессия того, у кого id меньше: это правило одинаково считается с обеих сторон
    static Session accept(Session existing, Ratchet ratchet, byte[] associated, Offer offer, boolean myIdIsSmaller) {
        Leg incoming = new Leg(ratchet, associated, offer.ephemeral(), false);
        if (existing == null) {
            Session session = new Session();
            session.current = incoming;
            return session;
        }
        if (existing.pending != null && myIdIsSmaller) {
            existing.fallback = incoming;
        } else {
            existing.fallback = existing.current;
            existing.current = incoming;
            existing.pending = null;
        }
        return existing;
    }

    private Leg leg(byte[] ephemeral) {
        if (current != null && Arrays.equals(current.ephemeral, ephemeral)) {
            return current;
        }
        return fallback != null && Arrays.equals(fallback.ephemeral, ephemeral) ? fallback : null;
    }

    // ---- хранение ----

    JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.add("current", current.toJson());
        if (fallback != null) {
            json.add("fallback", fallback.toJson());
        }
        if (pending != null) {
            JsonObject offer = new JsonObject();
            offer.addProperty("ephemeral", Bytes.base64(pending.ephemeral()));
            offer.addProperty("signedId", pending.signedId());
            offer.addProperty("oneTimeId", pending.oneTimeId());
            offer.addProperty("postQuantumId", pending.postQuantumId());
            if (pending.kemCiphertext() != null) {
                offer.addProperty("kemCiphertext", Bytes.base64(pending.kemCiphertext()));
            }
            json.add("pending", offer);
        }
        return json;
    }

    static Session fromJson(JsonObject json) throws GeneralSecurityException {
        Session session = new Session();
        session.current = Leg.fromJson(json.getAsJsonObject("current"));
        session.fallback = Leg.fromJson(json.getAsJsonObject("fallback"));
        JsonObject offer = json.getAsJsonObject("pending");
        if (offer != null) {
            session.pending = new Offer(Bytes.fromBase64(offer.get("ephemeral").getAsString()), offer.get("signedId").getAsLong(),
                    offer.get("oneTimeId").getAsLong(), offer.get("postQuantumId").getAsLong(),
                    offer.has("kemCiphertext") ? Bytes.fromBase64(offer.get("kemCiphertext").getAsString()) : null);
        }
        return session;
    }
}
