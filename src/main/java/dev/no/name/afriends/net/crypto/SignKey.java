package dev.no.name.afriends.net.crypto;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

// пара Ed25519. Каждая подпись идёт с меткой назначения: подпись предключа не выдать за вход и наоборот
public final class SignKey {
    public static final int KEY_BYTES = 32;
    public static final int SIGNATURE_BYTES = 64;
    private static final String ALGORITHM = "Ed25519";
    // SEQUENCE { SEQUENCE { OID 1.3.101.112 }, BIT STRING } — заголовок X.509 для Ed25519
    private static final byte[] X509_HEADER = {0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00};

    private final PrivateKey secret;
    private final byte[] publicRaw;

    private SignKey(PrivateKey secret, byte[] publicRaw) {
        this.secret = secret;
        this.publicRaw = publicRaw;
    }

    public static SignKey generate() {
        try {
            KeyPair pair = KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair();
            byte[] encoded = pair.getPublic().getEncoded();
            return new SignKey(pair.getPrivate(), Arrays.copyOfRange(encoded, encoded.length - KEY_BYTES, encoded.length));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Ed25519 недоступен", e);
        }
    }

    public static SignKey restore(byte[] pkcs8, byte[] publicRaw) throws GeneralSecurityException {
        if (publicRaw == null || publicRaw.length != KEY_BYTES) {
            throw new GeneralSecurityException("ключ Ed25519 не 32 байта");
        }
        return new SignKey(KeyFactory.getInstance(ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(pkcs8)), publicRaw.clone());
    }

    public byte[] publicRaw() {
        return publicRaw.clone();
    }

    public byte[] encodedSecret() {
        return secret.getEncoded();
    }

    public byte[] sign(SignLabel label, byte[] message) {
        try {
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initSign(secret);
            signature.update(label.bytes());
            signature.update(message);
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("подпись Ed25519 не удалась", e);
        }
    }

    public static boolean verify(byte[] signerRaw, SignLabel label, byte[] message, byte[] signatureBytes) {
        if (signerRaw == null || signerRaw.length != KEY_BYTES || signatureBytes == null || signatureBytes.length != SIGNATURE_BYTES) {
            return false;
        }
        try {
            byte[] x509 = Arrays.copyOf(X509_HEADER, X509_HEADER.length + KEY_BYTES);
            System.arraycopy(signerRaw, 0, x509, X509_HEADER.length, KEY_BYTES);
            PublicKey key = KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(x509));
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initVerify(key);
            signature.update(label.bytes());
            signature.update(message);
            return signature.verify(signatureBytes);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }
}
