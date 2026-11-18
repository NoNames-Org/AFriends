package dev.no.name.afriends.net.media;

import dev.no.name.afriends.net.crypto.ProtocolLabels;
import dev.no.name.afriends.net.crypto.Aead;
import dev.no.name.afriends.net.crypto.Bytes;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Шифрование вложений: на каждую картинку свой случайный ключ (ChaCha20-Poly1305), ключ уходит другу
 * внутри сквозного сообщения. На сервер ложится nonce + шифротекст + тег — без ключа это просто шум
 */
public final class MediaCrypto {
    private static final byte[] LABEL = ProtocolLabels.MEDIA.getBytes(StandardCharsets.US_ASCII);
    private static final SecureRandom RANDOM = new SecureRandom();

    private MediaCrypto() {
    }

    public static byte[] newKey() {
        byte[] key = new byte[Aead.KEY_BYTES];
        RANDOM.nextBytes(key);
        return key;
    }

    public static byte[] seal(byte[] key, byte[] plain) throws GeneralSecurityException {
        byte[] nonce = new byte[Aead.NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        return Bytes.concat(nonce, Aead.seal(key, nonce, LABEL, plain));
    }

    // бросает, если файл подменён, повреждён или ключ не тот
    public static byte[] open(byte[] key, byte[] sealed) throws GeneralSecurityException {
        if (sealed.length < Aead.NONCE_BYTES + Aead.TAG_BYTES || key.length != Aead.KEY_BYTES) {
            throw new GeneralSecurityException("вложение короче заголовка");
        }
        byte[] nonce = Arrays.copyOf(sealed, Aead.NONCE_BYTES);
        return Aead.open(key, nonce, LABEL, Arrays.copyOfRange(sealed, Aead.NONCE_BYTES, sealed.length));
    }
}
