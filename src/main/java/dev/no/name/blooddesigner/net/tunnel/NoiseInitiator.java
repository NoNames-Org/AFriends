package dev.no.name.blooddesigner.net.tunnel;

import dev.no.name.blooddesigner.net.crypto.Aead;
import dev.no.name.blooddesigner.net.crypto.Bytes;
import dev.no.name.blooddesigner.net.crypto.DhKey;
import dev.no.name.blooddesigner.net.crypto.Hkdf;

import java.security.GeneralSecurityException;
import java.util.Arrays;

// Noise_NK_25519_ChaChaPoly_SHA256, сторона инициатора (cloud/docs/CRYPTO.md, раздел 4).
// Открытый ключ сервера известен заранее: только его владелец сможет закончить рукопожатие,
// поэтому тот, кто вскрыл TLS, не выдаст себя за сервер
//   -> e, es
//   <- e, ee
final class NoiseInitiator {
    // имя протокола ровно 32 байта, поэтому оно само становится начальным хешем
    private static final byte[] PROTOCOL_NAME = Bytes.ascii("Noise_NK_25519_ChaChaPoly_SHA256");
    private static final byte[] PROLOGUE = Bytes.ascii("smphelper-link-tunnel-v1");
    static final int MESSAGE_BYTES = DhKey.KEY_BYTES + Aead.TAG_BYTES;

    record Transport(CipherState send, CipherState receive, byte[] handshakeHash) {
    }

    private final byte[] serverStatic;
    private final DhKey ephemeral = DhKey.generate();
    private byte[] chainingKey = PROTOCOL_NAME.clone();
    private byte[] hash = PROTOCOL_NAME.clone();
    private CipherState cipher;

    NoiseInitiator(byte[] serverStatic) {
        this.serverStatic = serverStatic.clone();
        mixHash(PROLOGUE);
        // пред-сообщение схемы NK: статический ключ ответчика
        mixHash(this.serverStatic);
    }

    byte[] writeHello() throws GeneralSecurityException {
        byte[] ephemeralPublic = ephemeral.publicRaw();
        mixHash(ephemeralPublic);
        mixKey(ephemeral.agree(serverStatic));
        // нагрузка пустая намеренно: у первого сообщения нет прямой секретности и его можно повторить
        return Bytes.concat(ephemeralPublic, encryptAndHash(Bytes.EMPTY));
    }

    // бросает, если ответил не владелец зашитого ключа сервера
    Transport readReply(byte[] message) throws GeneralSecurityException {
        if (message.length != MESSAGE_BYTES) {
            throw new GeneralSecurityException("неверная длина ответа сервера");
        }
        byte[] remoteEphemeral = Arrays.copyOf(message, DhKey.KEY_BYTES);
        mixHash(remoteEphemeral);
        mixKey(ephemeral.agree(remoteEphemeral));
        if (decryptAndHash(Arrays.copyOfRange(message, DhKey.KEY_BYTES, message.length)).length != 0) {
            throw new GeneralSecurityException("нагрузка ответа должна быть пустой");
        }
        // первый ключ шифрует инициатор -> ответчик, второй ответчик -> инициатор
        byte[][] keys = hkdf2(chainingKey, Bytes.EMPTY);
        return new Transport(new CipherState(keys[0]), new CipherState(keys[1]), hash.clone());
    }

    private void mixHash(byte[] data) {
        hash = Hkdf.sha256(hash, data);
    }

    private void mixKey(byte[] inputKeyMaterial) {
        byte[][] keys = hkdf2(chainingKey, inputKeyMaterial);
        Bytes.wipe(inputKeyMaterial);
        chainingKey = keys[0];
        cipher = new CipherState(keys[1]);
    }

    private byte[] encryptAndHash(byte[] plaintext) throws GeneralSecurityException {
        byte[] ciphertext = cipher.encrypt(hash, plaintext);
        mixHash(ciphertext);
        return ciphertext;
    }

    private byte[] decryptAndHash(byte[] ciphertext) throws GeneralSecurityException {
        byte[] plaintext = cipher.decrypt(hash, ciphertext);
        mixHash(ciphertext);
        return plaintext;
    }

    // HKDF из Noise на два выхода
    private static byte[][] hkdf2(byte[] chainingKey, byte[] inputKeyMaterial) {
        byte[] temp = Hkdf.hmac(chainingKey, inputKeyMaterial);
        byte[] first = Hkdf.hmac(temp, new byte[] {1});
        byte[] second = Hkdf.hmac(temp, first, new byte[] {2});
        Bytes.wipe(temp);
        return new byte[][] {first, second};
    }
}
