package dev.no.name.afriends.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Разбор строки чата сервера вида «[PM] Bob → Fell: текст» в личное сообщение. */
public final class ServerMessageParser {
    private static final Pattern PM = Pattern.compile(
            "^\\s*\\[(?:PM|ЛС)]\\s*([A-Za-z0-9_]{1,16})\\s*(?:→|->|»|>)\\s*([A-Za-z0-9_]{1,16})\\s*:\\s?(.*)$");

    public record Parsed(String partner, PrivateMessage message) {
    }

    private ServerMessageParser() {
    }

    /** null — это не личное сообщение или оно не про нас. */
    public static Parsed parse(String plain, String selfName, long now) {
        Matcher matcher = PM.matcher(plain);
        if (!matcher.matches() || selfName == null) {
            return null;
        }
        String from = matcher.group(1);
        String to = matcher.group(2);
        String text = matcher.group(3);
        if (from.equalsIgnoreCase(selfName)) {
            return new Parsed(to, new PrivateMessage(true, text, now));
        }
        if (to.equalsIgnoreCase(selfName)) {
            return new Parsed(from, new PrivateMessage(false, text, now));
        }
        return null;
    }
}
