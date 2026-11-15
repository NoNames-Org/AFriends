package dev.no.name.blooddesigner.net.crypto;

import javax.crypto.KeyAgreement;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

// пара X25519: секрет остаётся внутри, наружу — только открытые 32 байта и общий секрет
public final class DhKey {
    public static final int KEY_BYTES = 32;
    private static final String ALGORITHM = "X25519";
    // SEQUENCE { SEQUENCE { OID 1.3.101.110 }, BIT STRING } — заголовок X.509 для X25519
    private static final byte[] X509_HEADER = {0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6e, 0x03, 0x21, 0x00};

    private final PrivateKey secret;
    private final byte[] publicRaw;

    private DhKey(PrivateKey secret, byte[] publicRaw) {
        this.secret = secret;
        this.publicRaw = publicRaw;
    }

    public static DhKey generate() {
        try {
            KeyPair pair = KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair();
            return new DhKey(pair.getPrivate(), raw(pair.getPublic()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("X25519 недоступен", e);
        }
    }

    // из сохранённого секрета (PKCS#8) и открытых 32 байт
    public static DhKey restore(byte[] pkcs8, byte[] publicRaw) throws GeneralSecurityException {
        requireKey(publicRaw);
        return new DhKey(KeyFactory.getInstance(ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(pkcs8)), publicRaw.clone());
    }

    public byte[] publicRaw() {
        return publicRaw.clone();
    }

    // секрет в PKCS#8 — только для записи в свой файл
    public byte[] encodedSecret() {
        return secret.getEncoded();
    }

    // общий секрет с чужим открытым ключом. Результат из одних нулей (точка малого порядка) — отказ
    public byte[] agree(byte[] peerRaw) throws GeneralSecurityException {
        KeyAgreement agreement = KeyAgreement.getInstance(ALGORITHM);
        agreement.init(secret);
        agreement.doPhase(publicFromRaw(peerRaw), true);
        byte[] shared = agreement.generateSecret();
        byte zero = 0;
        for (byte value : shared) {
            zero |= value;
        }
        if (zero == 0) {
            throw new GeneralSecurityException("общий секрет из нулей");
        }
        return shared;
    }

    private static PublicKey publicFromRaw(byte[] raw) throws GeneralSecurityException {
        requireKey(raw);
        byte[] x509 = Arrays.copyOf(X509_HEADER, X509_HEADER.length + KEY_BYTES);
        System.arraycopy(raw, 0, x509, X509_HEADER.length, KEY_BYTES);
        return KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(x509));
    }

    private static void requireKey(byte[] raw) throws GeneralSecurityException {
        if (raw == null || raw.length != KEY_BYTES) {
            throw new GeneralSecurityException("ключ X25519 не 32 байта");
        }
    }

    private static byte[] raw(PublicKey key) {
        byte[] encoded = key.getEncoded();
        return Arrays.copyOfRange(encoded, encoded.length - KEY_BYTES, encoded.length);
    }
}
