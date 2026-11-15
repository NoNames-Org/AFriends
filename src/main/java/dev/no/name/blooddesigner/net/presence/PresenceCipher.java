package dev.no.name.blooddesigner.net.presence;

import dev.no.name.blooddesigner.net.crypto.Aead;
import dev.no.name.blooddesigner.net.crypto.Bytes;
import dev.no.name.blooddesigner.net.e2e.E2eException;
import dev.no.name.blooddesigner.net.e2e.Padding;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

// Блоб статуса (CRYPTO.md, раздел 7а): 0x02 || эпоха || nonce || шифротекст.
// AD связывает блоб с парой «от кого — кому», так что сервер не переложит его другому другу
final class PresenceCipher {
    private static final byte VERSION = 0x02;
    private static final byte[] LABEL = Bytes.ascii("smphelper-presence-v2");
    private static final int HEADER = 1 + 4 + Aead.NONCE_BYTES;
    private static final SecureRandom RANDOM = new SecureRandom();

    // зашифрованный блоб и эпоха ключа, которым он закрыт
    record Sealed(long epoch, byte[] nonce, byte[] ciphertext) {
    }

    private PresenceCipher() {
    }

    static byte[] seal(byte[] key, long epoch, String fromId, String toId, byte[] plaintext) throws E2eException {
        byte[] nonce = new byte[Aead.NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        try {
            byte[] ciphertext = Aead.seal(key, nonce, associated(fromId, toId), Padding.pad(plaintext));
            return Bytes.concat(new byte[] {VERSION}, Bytes.u32(epoch), nonce, ciphertext);
        } catch (GeneralSecurityException e) {
            throw new E2eException("presence_seal", e);
        }
    }

    // разбор без расшифровки: сначала надо узнать эпоху, чтобы найти ключ
    static Sealed parse(byte[] blob) throws E2eException {
        if (blob.length < HEADER + Aead.TAG_BYTES || blob[0] != VERSION) {
            throw new E2eException("bad_presence");
        }
        return new Sealed(Bytes.readU32(blob, 1), Arrays.copyOfRange(blob, 5, HEADER), Arrays.copyOfRange(blob, HEADER, blob.length));
    }

    static byte[] open(byte[] key, Sealed sealed, String fromId, String toId) throws E2eException {
        try {
            return Padding.unpad(Aead.open(key, sealed.nonce(), associated(fromId, toId), sealed.ciphertext()));
        } catch (GeneralSecurityException e) {
            throw new E2eException("bad_presence", e);
        }
    }

    private static byte[] associated(String fromId, String toId) {
        return Bytes.concat(LABEL, Bytes.ascii(fromId), Bytes.ascii(toId));
    }
}
