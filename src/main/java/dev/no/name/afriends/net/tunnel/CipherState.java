package dev.no.name.afriends.net.tunnel;

import dev.no.name.afriends.net.crypto.Aead;

import java.security.GeneralSecurityException;

// CipherState из Noise: один ключ на направление, nonce = 4 нуля + счётчик 64 бита little-endian.
// Счётчик растёт на каждый кадр, поэтому пара «ключ + nonce» не повторяется никогда
final class CipherState {
    private final byte[] key;
    private long counter;

    CipherState(byte[] key) {
        this.key = key;
    }

    byte[] encrypt(byte[] associated, byte[] plaintext) throws GeneralSecurityException {
        return Aead.seal(key, nextNonce(), associated, plaintext);
    }

    byte[] decrypt(byte[] associated, byte[] ciphertext) throws GeneralSecurityException {
        return Aead.open(key, nextNonce(), associated, ciphertext);
    }

    private byte[] nextNonce() throws GeneralSecurityException {
        if (counter == -1L) {
            throw new GeneralSecurityException("счётчик nonce исчерпан");
        }
        byte[] nonce = new byte[Aead.NONCE_BYTES];
        long value = counter++;
        for (int i = 0; i < 8; i++) {
            nonce[4 + i] = (byte) (value >>> (8 * i));
        }
        return nonce;
    }
}
