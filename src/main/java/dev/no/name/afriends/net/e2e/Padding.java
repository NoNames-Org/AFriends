package dev.no.name.afriends.net.e2e;

import java.util.Arrays;

// Дополнение текста до корзины: данные || 0x80 || нули. Снаружи видна только корзина, а не точная длина
public final class Padding {
    private static final int[] BUCKETS = {128, 256, 512, 1024, 2048, 4096, 8192};
    static final int MAX_PLAINTEXT = BUCKETS[BUCKETS.length - 1] - 1;

    private Padding() {
    }

    public static byte[] pad(byte[] data) throws E2eException {
        for (int bucket : BUCKETS) {
            if (data.length < bucket) {
                byte[] padded = Arrays.copyOf(data, bucket);
                padded[data.length] = (byte) 0x80;
                return padded;
            }
        }
        throw new E2eException("too_long");
    }

    public static byte[] unpad(byte[] padded) throws E2eException {
        for (int i = padded.length - 1; i >= 0; i--) {
            if (padded[i] == (byte) 0x80) {
                return Arrays.copyOf(padded, i);
            }
            if (padded[i] != 0) {
                break;
            }
        }
        throw new E2eException("bad_padding");
    }
}
