package dev.no.name.afriends.net.crypto;

import javax.crypto.KEM;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

// Постквантовый ключ ML-KEM-768 (FIPS 203). Алгоритм есть в Java начиная с 24: на Java 21 (Minecraft 1.21.11)
// available() вернёт false, и сессии ставятся без постквантовой части
public final class KemKey {
    public static final int PUBLIC_BYTES = 1184;
    public static final int CIPHERTEXT_BYTES = 1088;
    private static final String ALGORITHM = "ML-KEM-768";
    // заголовок X.509 перед открытым ключом; узнаём у самой Java, чтобы не зашивать DER руками
    private static final byte[] X509_HEADER = probeHeader();

    public record Encapsulated(byte[] secret, byte[] ciphertext) {
    }

    private final PrivateKey secret;
    private final byte[] publicRaw;

    private KemKey(PrivateKey secret, byte[] publicRaw) {
        this.secret = secret;
        this.publicRaw = publicRaw;
    }

    public static boolean available() {
        return X509_HEADER != null;
    }

    public static KemKey generate() throws GeneralSecurityException {
        KeyPair pair = KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair();
        return new KemKey(pair.getPrivate(), raw(pair.getPublic()));
    }

    public static KemKey restore(byte[] pkcs8, byte[] publicRaw) throws GeneralSecurityException {
        requirePublic(publicRaw);
        return new KemKey(KeyFactory.getInstance(ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(pkcs8)), publicRaw.clone());
    }

    public byte[] publicRaw() {
        return publicRaw.clone();
    }

    public byte[] encodedSecret() {
        return secret.getEncoded();
    }

    // общий секрет и шифротекст для владельца открытого ключа
    public static Encapsulated encapsulate(byte[] peerRaw) throws GeneralSecurityException {
        requirePublic(peerRaw);
        if (!available()) {
            throw new GeneralSecurityException("ML-KEM-768 недоступен в этой Java");
        }
        byte[] x509 = Arrays.copyOf(X509_HEADER, X509_HEADER.length + PUBLIC_BYTES);
        System.arraycopy(peerRaw, 0, x509, X509_HEADER.length, PUBLIC_BYTES);
        PublicKey key = KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(x509));
        KEM.Encapsulated result = KEM.getInstance(ALGORITHM).newEncapsulator(key).encapsulate();
        return new Encapsulated(result.key().getEncoded(), result.encapsulation());
    }

    public byte[] decapsulate(byte[] ciphertext) throws GeneralSecurityException {
        if (ciphertext == null || ciphertext.length != CIPHERTEXT_BYTES) {
            throw new GeneralSecurityException("шифротекст ML-KEM неверной длины");
        }
        return KEM.getInstance(ALGORITHM).newDecapsulator(secret).decapsulate(ciphertext).getEncoded();
    }

    private static void requirePublic(byte[] raw) throws GeneralSecurityException {
        if (raw == null || raw.length != PUBLIC_BYTES) {
            throw new GeneralSecurityException("ключ ML-KEM-768 неверной длины");
        }
    }

    private static byte[] raw(PublicKey key) {
        byte[] encoded = key.getEncoded();
        return Arrays.copyOfRange(encoded, encoded.length - PUBLIC_BYTES, encoded.length);
    }

    private static byte[] probeHeader() {
        try {
            byte[] encoded = KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair().getPublic().getEncoded();
            return encoded.length > PUBLIC_BYTES ? Arrays.copyOf(encoded, encoded.length - PUBLIC_BYTES) : null;
        } catch (GeneralSecurityException | RuntimeException e) {
            return null;
        }
    }
}
