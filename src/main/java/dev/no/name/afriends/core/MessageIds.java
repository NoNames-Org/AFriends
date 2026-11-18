package dev.no.name.afriends.core;

import java.security.SecureRandom;
import java.util.HexFormat;

// номер сообщения: 8 случайных байт. Для сети его придумывает отправитель, заметкам — сам мод (по нему их узнают у себя)
public final class MessageIds {
    private static final SecureRandom RANDOM = new SecureRandom();

    private MessageIds() {
    }

    public static String next() {
        byte[] id = new byte[8];
        RANDOM.nextBytes(id);
        return HexFormat.of().formatHex(id);
    }
}
