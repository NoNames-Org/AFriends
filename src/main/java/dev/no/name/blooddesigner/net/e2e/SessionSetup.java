package dev.no.name.blooddesigner.net.e2e;

import dev.no.name.blooddesigner.net.crypto.Bytes;
import dev.no.name.blooddesigner.net.crypto.DhKey;
import dev.no.name.blooddesigner.net.crypto.Hkdf;
import dev.no.name.blooddesigner.net.crypto.KemKey;
import dev.no.name.blooddesigner.net.identity.Identity;

import java.security.GeneralSecurityException;
import java.util.Arrays;

// Установка сессии по схеме PQXDH (cloud/docs/CRYPTO.md, раздел 6).
// Обе стороны выводят один ключ SK: DH1 и DH2 доказывают личности, DH3 и DH4 дают прямую секретность,
// секрет ML-KEM защищает от того, кто пишет трафик в расчёте на квантовый компьютер
final class SessionSetup {
    private static final String INFO = "smphelper-pqxdh-v1";
    private static final byte[] DOMAIN = Bytes.ascii("smphelper-e2e-v1");
    private static final byte[] ZERO_SALT = new byte[Hkdf.HASH_BYTES];
    private static final int KEY_BYTES = 32;

    record Initiated(byte[] sharedKey, Offer offer) {
    }

    private SessionSetup() {
    }

    static Initiated initiate(Identity me, PrekeyBundle bundle) throws E2eException {
        try {
            DhKey ephemeral = DhKey.generate();
            byte[] dh1 = me.exchange().agree(bundle.signedKey());
            byte[] dh2 = ephemeral.agree(bundle.keys().exchangeKey());
            byte[] dh3 = ephemeral.agree(bundle.signedKey());
            byte[] dh4 = bundle.hasOneTime() ? ephemeral.agree(bundle.oneTimeKey()) : Bytes.EMPTY;
            // на Java 21 у нас нет ML-KEM: сессия ставится без постквантовой части
            KemKey.Encapsulated kem = bundle.hasPostQuantum() && KemKey.available() ? KemKey.encapsulate(bundle.postQuantumKey()) : null;
            byte[] sharedKey = derive(dh1, dh2, dh3, dh4, kem == null ? Bytes.EMPTY : kem.secret());
            Offer offer = new Offer(ephemeral.publicRaw(), bundle.signedId(), bundle.hasOneTime() ? bundle.oneTimeId() : PrekeyBundle.NONE,
                    kem == null ? PrekeyBundle.NONE : bundle.postQuantumId(), kem == null ? null : kem.ciphertext());
            return new Initiated(sharedKey, offer);
        } catch (GeneralSecurityException e) {
            throw new E2eException("setup_failed", e);
        }
    }

    // сторона получателя: тот же ключ из своих секретов. Одноразовый предключ здесь только читается
    static byte[] respond(Identity me, PrekeyRing ring, FriendKeys sender, Offer offer) throws E2eException {
        DhKey signed = ring.signed(offer.signedId());
        if (signed == null) {
            throw new E2eException("unknown_signed_prekey");
        }
        try {
            byte[] dh1 = signed.agree(sender.exchangeKey());
            byte[] dh2 = me.exchange().agree(offer.ephemeral());
            byte[] dh3 = signed.agree(offer.ephemeral());
            byte[] dh4 = Bytes.EMPTY;
            if (offer.oneTimeId() != PrekeyBundle.NONE) {
                DhKey oneTime = ring.oneTime(offer.oneTimeId());
                if (oneTime == null) {
                    throw new E2eException("unknown_one_time_prekey");
                }
                dh4 = oneTime.agree(offer.ephemeral());
            }
            byte[] secret = Bytes.EMPTY;
            if (offer.postQuantumId() != PrekeyBundle.NONE) {
                KemKey kem = ring.postQuantum(offer.postQuantumId());
                if (kem == null) {
                    throw new E2eException("unknown_pq_prekey");
                }
                secret = kem.decapsulate(offer.kemCiphertext());
            }
            return derive(dh1, dh2, dh3, dh4, secret);
        } catch (GeneralSecurityException e) {
            throw new E2eException("setup_failed", e);
        }
    }

    // привязка шифротекстов к личностям обеих сторон; первым идёт тот, кто начал сессию
    static byte[] associated(byte[] initiatorSign, byte[] initiatorExchange, byte[] responderSign, byte[] responderExchange) {
        return Bytes.concat(DOMAIN, initiatorSign, initiatorExchange, responderSign, responderExchange);
    }

    private static byte[] derive(byte[] dh1, byte[] dh2, byte[] dh3, byte[] dh4, byte[] kemSecret) {
        byte[] prefix = new byte[KEY_BYTES];
        Arrays.fill(prefix, (byte) 0xFF);
        byte[] material = Bytes.concat(prefix, dh1, dh2, dh3, dh4, kemSecret);
        byte[] key = Hkdf.derive(ZERO_SALT, material, INFO, KEY_BYTES);
        Bytes.wipe(material, dh1, dh2, dh3, dh4, kemSecret);
        return key;
    }
}
