package dev.no.name.afriends.net.crypto;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

// мелкие помощники для байтов, общие для туннеля и сквозного шифрования
public final class Bytes {
    public static final byte[] EMPTY = new byte[0];

    private Bytes() {
    }

    public static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.write(part, 0, part.length);
        }
        return out.toByteArray();
    }

    public static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    // 32 бита без знака, big-endian
    public static byte[] u32(long value) {
        return new byte[] {(byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value};
    }

    public static long readU32(byte[] data, int offset) {
        return ((data[offset] & 0xFFL) << 24) | ((data[offset + 1] & 0xFFL) << 16)
                | ((data[offset + 2] & 0xFFL) << 8) | (data[offset + 3] & 0xFFL);
    }

    public static String base64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    // бросает IllegalArgumentException на мусоре
    public static byte[] fromBase64(String text) {
        return Base64.getDecoder().decode(text);
    }

    // секреты после использования затираются, чтобы не лежали в памяти до сборки мусора
    public static void wipe(byte[]... secrets) {
        for (byte[] secret : secrets) {
            if (secret != null) {
                Arrays.fill(secret, (byte) 0);
            }
        }
    }
}
