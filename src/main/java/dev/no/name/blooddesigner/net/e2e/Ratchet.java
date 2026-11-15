package dev.no.name.blooddesigner.net.e2e;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.no.name.blooddesigner.net.crypto.Aead;
import dev.no.name.blooddesigner.net.crypto.Bytes;
import dev.no.name.blooddesigner.net.crypto.DhKey;
import dev.no.name.blooddesigner.net.crypto.Hkdf;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

// Double Ratchet (алгоритм Signal, cloud/docs/CRYPTO.md, раздел 7). У каждого сообщения свой ключ,
// который стирается сразу после использования: кража ключей сегодня не раскрывает вчерашнюю переписку,
// а после следующего обмена DH-ключами — и завтрашнюю
final class Ratchet {
    static final int HEADER_BYTES = DhKey.KEY_BYTES + 8;
    private static final String RATCHET_INFO = "smphelper-ratchet-v1";
    private static final String MESSAGE_INFO = "smphelper-msg-v1";
    private static final byte[] ZERO_SALT = new byte[Hkdf.HASH_BYTES];
    private static final byte[] MESSAGE_STEP = {1};
    private static final byte[] CHAIN_STEP = {2};
    private static final int MAX_SKIP = 500;
    private static final int MAX_STORED_SKIPPED = 2000;
    private static final long SKIPPED_TTL_MS = 30L * 86_400_000;
    private static final long MAX_COUNTER = 1L << 31;

    private record Skipped(byte[] key, long storedAt) {
    }

    private DhKey self;
    private byte[] remote;
    private byte[] rootKey;
    private byte[] sendChain;
    private byte[] receiveChain;
    private long sent;
    private long received;
    private long previousSent;
    private Map<String, Skipped> skipped = new LinkedHashMap<>();

    private Ratchet() {
    }

    // начавший сессию: первый шаг храповика делается сразу, с подписанным предключом получателя
    static Ratchet initiator(byte[] sharedKey, byte[] remoteSignedPrekey) throws E2eException {
        Ratchet ratchet = new Ratchet();
        ratchet.self = DhKey.generate();
        ratchet.remote = remoteSignedPrekey.clone();
        byte[][] keys = ratchet.rootStep(sharedKey, remoteSignedPrekey);
        ratchet.rootKey = keys[0];
        ratchet.sendChain = keys[1];
        return ratchet;
    }

    // получатель: его первый ключ храповика — тот самый подписанный предключ
    static Ratchet responder(byte[] sharedKey, DhKey signedPrekey) {
        Ratchet ratchet = new Ratchet();
        ratchet.self = signedPrekey;
        ratchet.rootKey = sharedKey.clone();
        return ratchet;
    }

    // заголовок(40) || шифротекст
    byte[] encrypt(byte[] plaintext, byte[] associated) throws E2eException {
        if (sendChain == null || sent >= MAX_COUNTER) {
            throw new E2eException("cannot_send");
        }
        byte[] messageKey = Hkdf.hmac(sendChain, MESSAGE_STEP);
        sendChain = Hkdf.hmac(sendChain, CHAIN_STEP);
        byte[] header = Bytes.concat(self.publicRaw(), Bytes.u32(previousSent), Bytes.u32(sent));
        sent++;
        try {
            return Bytes.concat(header, seal(messageKey, plaintext, Bytes.concat(associated, header), true));
        } catch (GeneralSecurityException e) {
            throw new E2eException("encrypt_failed", e);
        }
    }

    // Состояние меняется только если сообщение настоящее: расшифровка идёт на копии,
    // и подделка или сообщение из чужой сессии не сдвигают храповик
    byte[] decrypt(byte[] message, byte[] associated, long now) throws E2eException {
        if (message.length < HEADER_BYTES + Aead.TAG_BYTES) {
            throw new E2eException("bad_message");
        }
        byte[] header = Arrays.copyOf(message, HEADER_BYTES);
        byte[] body = Arrays.copyOfRange(message, HEADER_BYTES, message.length);
        byte[] remoteKey = Arrays.copyOf(header, DhKey.KEY_BYTES);
        long previousCount = Bytes.readU32(header, DhKey.KEY_BYTES);
        long number = Bytes.readU32(header, DhKey.KEY_BYTES + 4);

        Ratchet trial = copy();
        byte[] messageKey = trial.messageKeyFor(remoteKey, previousCount, number, now);
        byte[] plaintext;
        try {
            plaintext = seal(messageKey, body, Bytes.concat(associated, header), false);
        } catch (GeneralSecurityException e) {
            throw new E2eException("bad_message", e);
        }
        adopt(trial);
        return plaintext;
    }

    private byte[] messageKeyFor(byte[] remoteKey, long previousCount, long number, long now) throws E2eException {
        Skipped stored = skipped.remove(skippedId(remoteKey, number));
        if (stored != null) {
            // копия: ключ затирается после попытки, а при подделке отложенный ключ должен уцелеть
            return stored.key().clone();
        }
        if (remote == null || !Arrays.equals(remote, remoteKey)) {
            skipUntil(previousCount, now);
            turn(remoteKey);
        }
        skipUntil(number, now);
        byte[] messageKey = Hkdf.hmac(receiveChain, MESSAGE_STEP);
        receiveChain = Hkdf.hmac(receiveChain, CHAIN_STEP);
        received++;
        return messageKey;
    }

    // сообщения могут прийти не по порядку: ключи пропущенных откладываются, но не бесконечно
    private void skipUntil(long until, long now) throws E2eException {
        if (receiveChain == null) {
            return;
        }
        if (until - received > MAX_SKIP) {
            throw new E2eException("too_many_skipped");
        }
        while (received < until) {
            skipped.put(skippedId(remote, received), new Skipped(Hkdf.hmac(receiveChain, MESSAGE_STEP), now));
            receiveChain = Hkdf.hmac(receiveChain, CHAIN_STEP);
            received++;
        }
        Iterator<Skipped> oldest = skipped.values().iterator();
        while (oldest.hasNext()) {
            Skipped entry = oldest.next();
            if (skipped.size() <= MAX_STORED_SKIPPED && now - entry.storedAt() <= SKIPPED_TTL_MS) {
                break;
            }
            oldest.remove();
        }
    }

    // шаг DH-храповика: собеседник прислал новый ключ, обе цепочки обновляются
    private void turn(byte[] remoteKey) throws E2eException {
        previousSent = sent;
        sent = 0;
        received = 0;
        remote = remoteKey.clone();
        byte[][] receiving = rootStep(rootKey, remote);
        rootKey = receiving[0];
        receiveChain = receiving[1];
        self = DhKey.generate();
        byte[][] sending = rootStep(rootKey, remote);
        rootKey = sending[0];
        sendChain = sending[1];
    }

    private byte[][] rootStep(byte[] root, byte[] remoteKey) throws E2eException {
        try {
            byte[] shared = self.agree(remoteKey);
            byte[] out = Hkdf.derive(root, shared, RATCHET_INFO, 64);
            Bytes.wipe(shared);
            return new byte[][] {Arrays.copyOf(out, 32), Arrays.copyOfRange(out, 32, 64)};
        } catch (GeneralSecurityException e) {
            throw new E2eException("bad_ratchet_key", e);
        }
    }

    // ключ сообщения разворачивается в ключ и nonce шифра; он одноразовый, поэтому nonce не повторится
    private static byte[] seal(byte[] messageKey, byte[] data, byte[] associated, boolean encrypt) throws GeneralSecurityException {
        byte[] material = Hkdf.derive(ZERO_SALT, messageKey, MESSAGE_INFO, Aead.KEY_BYTES + Aead.NONCE_BYTES);
        byte[] key = Arrays.copyOf(material, Aead.KEY_BYTES);
        byte[] nonce = Arrays.copyOfRange(material, Aead.KEY_BYTES, material.length);
        try {
            return encrypt ? Aead.seal(key, nonce, associated, data) : Aead.open(key, nonce, associated, data);
        } finally {
            Bytes.wipe(material, key, messageKey);
        }
    }

    private static String skippedId(byte[] remoteKey, long number) {
        return HexFormat.of().formatHex(remoteKey) + ":" + number;
    }

    private Ratchet copy() {
        Ratchet copy = new Ratchet();
        copy.self = self;
        copy.remote = remote;
        copy.rootKey = rootKey;
        copy.sendChain = sendChain;
        copy.receiveChain = receiveChain;
        copy.sent = sent;
        copy.received = received;
        copy.previousSent = previousSent;
        copy.skipped = new LinkedHashMap<>(skipped);
        return copy;
    }

    private void adopt(Ratchet other) {
        self = other.self;
        remote = other.remote;
        rootKey = other.rootKey;
        sendChain = other.sendChain;
        receiveChain = other.receiveChain;
        sent = other.sent;
        received = other.received;
        previousSent = other.previousSent;
        skipped = other.skipped;
    }

    // ---- хранение ----

    JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("selfSecret", Bytes.base64(self.encodedSecret()));
        json.addProperty("selfPublic", Bytes.base64(self.publicRaw()));
        put(json, "remote", remote);
        put(json, "rootKey", rootKey);
        put(json, "sendChain", sendChain);
        put(json, "receiveChain", receiveChain);
        json.addProperty("sent", sent);
        json.addProperty("received", received);
        json.addProperty("previousSent", previousSent);
        JsonArray keys = new JsonArray();
        skipped.forEach((id, entry) -> {
            JsonObject row = new JsonObject();
            row.addProperty("id", id);
            row.addProperty("key", Bytes.base64(entry.key()));
            row.addProperty("at", entry.storedAt());
            keys.add(row);
        });
        json.add("skipped", keys);
        return json;
    }

    static Ratchet fromJson(JsonObject json) throws GeneralSecurityException {
        Ratchet ratchet = new Ratchet();
        ratchet.self = DhKey.restore(Bytes.fromBase64(json.get("selfSecret").getAsString()), Bytes.fromBase64(json.get("selfPublic").getAsString()));
        ratchet.remote = get(json, "remote");
        ratchet.rootKey = get(json, "rootKey");
        ratchet.sendChain = get(json, "sendChain");
        ratchet.receiveChain = get(json, "receiveChain");
        ratchet.sent = json.get("sent").getAsLong();
        ratchet.received = json.get("received").getAsLong();
        ratchet.previousSent = json.get("previousSent").getAsLong();
        for (JsonElement element : json.getAsJsonArray("skipped")) {
            JsonObject row = element.getAsJsonObject();
            ratchet.skipped.put(row.get("id").getAsString(),
                    new Skipped(Bytes.fromBase64(row.get("key").getAsString()), row.get("at").getAsLong()));
        }
        return ratchet;
    }

    private static void put(JsonObject json, String field, byte[] value) {
        if (value != null) {
            json.addProperty(field, Bytes.base64(value));
        }
    }

    private static byte[] get(JsonObject json, String field) {
        return json.has(field) ? Bytes.fromBase64(json.get(field).getAsString()) : null;
    }
}
