package dev.no.name.blooddesigner.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ServerMessages {
    private static final Pattern DELIMITER = Pattern.compile("^[^\\p{L}\\p{N}]*$");
    private static final Pattern OFFLINE = Pattern.compile("^([A-Za-z0-9_]{3,16}) is currently offline$");

    private ServerMessages() {
    }

    public static List<String> lines(String plain) {
        List<String> result = new ArrayList<>();
        for (String line : plain.split("\n")) {
            String trimmed = line.strip();
            if (!DELIMITER.matcher(trimmed).matches()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    public static boolean isBlock(String plain) {
        int newline = plain.indexOf('\n');
        return newline > 0 && DELIMITER.matcher(plain.substring(0, newline).strip()).matches();
    }

    public static String offlinePlayer(String plain) {
        List<String> lines = lines(plain);
        if (lines.size() != 1) {
            return null;
        }
        Matcher matcher = OFFLINE.matcher(lines.get(0));
        return matcher.matches() ? matcher.group(1) : null;
    }
}
