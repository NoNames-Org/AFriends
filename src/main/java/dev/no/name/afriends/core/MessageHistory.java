package dev.no.name.afriends.core;

public final class MessageHistory {
    public static final int DEFAULT = 100;
    public static final int MIN = 8;
    public static final int MAX = 1024;

    public static final int STEP = 8;

    private static int limit = DEFAULT;

    private MessageHistory() {
    }

    public static int limit() {
        return limit;
    }

    public static void set(int value) {
        limit = Math.max(MIN, Math.min(MAX, value));
    }
}
