package dev.no.name.afriends.net.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;

// SHA-256, HMAC-SHA256 и HKDF-SHA256 (RFC 5869)
public final class Hkdf {
    private static final String HMAC = "HmacSHA256";
    public static final int HASH_BYTES = 32;

    private Hkdf() {
    }

    public static byte[] sha256(byte[]... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (byte[] part : parts) {
                digest.update(part);
            }
            return digest.digest();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 недоступен", e);
        }
    }

    public static byte[] hmac(byte[] key, byte[]... parts) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            // пустой ключ SecretKeySpec не принимает; по RFC 2104 он равен ключу из нулей
            mac.init(new SecretKeySpec(key.length == 0 ? new byte[HASH_BYTES] : key, HMAC));
            for (byte[] part : parts) {
                mac.update(part);
            }
            return mac.doFinal();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 недоступен", e);
        }
    }

    // extract + expand: salt -> prk -> length байт
    public static byte[] derive(byte[] salt, byte[] inputKeyMaterial, String info, int length) {
        byte[] prk = hmac(salt, inputKeyMaterial);
        byte[] label = Bytes.ascii(info);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] block = Bytes.EMPTY;
        for (int counter = 1; out.size() < length; counter++) {
            block = hmac(prk, block, label, new byte[] {(byte) counter});
            out.write(block, 0, block.length);
        }
        Bytes.wipe(prk);
        return Arrays.copyOf(out.toByteArray(), length);
    }
}
