package dev.no.name.blooddesigner.net.chat;

import java.security.SecureRandom;
import java.util.HexFormat;

// номер сообщения для реакций: 8 случайных байт, его придумывает отправитель
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
