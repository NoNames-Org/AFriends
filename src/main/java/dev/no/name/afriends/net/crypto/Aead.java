package dev.no.name.afriends.net.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;

// ChaCha20-Poly1305 (RFC 8439): ключ 32 байта, nonce 12 байт, тег 16 байт в конце шифротекста
public final class Aead {
    public static final int KEY_BYTES = 32;
    public static final int NONCE_BYTES = 12;
    public static final int TAG_BYTES = 16;
    private static final String CIPHER = "ChaCha20-Poly1305";

    private Aead() {
    }

    public static byte[] seal(byte[] key, byte[] nonce, byte[] associated, byte[] plaintext) throws GeneralSecurityException {
        return run(Cipher.ENCRYPT_MODE, key, nonce, associated, plaintext);
    }

    // бросает, если шифротекст подделан, повреждён или зашифрован другим ключом
    public static byte[] open(byte[] key, byte[] nonce, byte[] associated, byte[] ciphertext) throws GeneralSecurityException {
        if (ciphertext.length < TAG_BYTES) {
            throw new GeneralSecurityException("шифротекст короче тега");
        }
        return run(Cipher.DECRYPT_MODE, key, nonce, associated, ciphertext);
    }

    private static byte[] run(int mode, byte[] key, byte[] nonce, byte[] associated, byte[] data) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(mode, new SecretKeySpec(key, "ChaCha20"), new IvParameterSpec(nonce));
        cipher.updateAAD(associated);
        return cipher.doFinal(data);
    }
}
