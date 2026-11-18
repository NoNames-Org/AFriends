package dev.no.name.afriends.net.e2e;

import dev.no.name.afriends.net.crypto.ProtocolLabels;
import dev.no.name.afriends.net.crypto.Bytes;
import dev.no.name.afriends.net.crypto.Hkdf;

import java.util.Arrays;

// Отпечаток пары собеседников: 12 групп по 5 цифр, одинаковые у обоих. Сверили голосом или в другом
// мессенджере — значит между вами никого нет, включая сервер
public final class SafetyNumber {
    private static final byte[] DOMAIN = Bytes.ascii(ProtocolLabels.SAFETY_DOMAIN);
    private static final int GROUPS = 12;

    private SafetyNumber() {
    }

    public static String of(byte[] mySign, byte[] myExchange, byte[] theirSign, byte[] theirExchange) {
        byte[] mine = Bytes.concat(mySign, myExchange);
        byte[] theirs = Bytes.concat(theirSign, theirExchange);
        boolean mineFirst = Arrays.compareUnsigned(mine, theirs) <= 0;
        byte[] hash = Hkdf.sha256(DOMAIN, mineFirst ? mine : theirs, mineFirst ? theirs : mine);
        StringBuilder text = new StringBuilder();
        for (int group = 0; group < GROUPS; group++) {
            // по 20 бит на группу из первых 30 байт хеша
            int offset = group * 5 / 2;
            int bits = group % 2 == 0
                    ? ((hash[offset] & 0xFF) << 12) | ((hash[offset + 1] & 0xFF) << 4) | ((hash[offset + 2] & 0xF0) >>> 4)
                    : ((hash[offset] & 0x0F) << 16) | ((hash[offset + 1] & 0xFF) << 8) | (hash[offset + 2] & 0xFF);
            if (group > 0) {
                text.append(' ');
            }
            text.append(String.format("%05d", bits % 100_000));
        }
        return text.toString();
    }
}
