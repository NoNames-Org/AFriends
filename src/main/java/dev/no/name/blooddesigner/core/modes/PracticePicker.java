package dev.no.name.blooddesigner.core.modes;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PracticePicker {
    private static final Pattern ONLINE = Pattern.compile("Online:\\s*(\\d+)\\s*/\\s*(\\d+)");
    private static final String NAME_PREFIX = "Practice #";

    public record Option(int slot, String name, List<String> lore) {
        public Option {
            lore = List.copyOf(lore);
        }
    }

    private PracticePicker() {
    }

    public static int pick(List<Option> options, int fallback) {
        int best = fallback;
        int bestFree = 0;
        for (Option option : options) {
            if (!option.name().startsWith(NAME_PREFIX)) {
                continue;
            }
            int free = freeSlots(option.lore());
            if (free > bestFree) {
                bestFree = free;
                best = option.slot();
            }
        }
        return best;
    }

    private static int freeSlots(List<String> lore) {
        for (String line : lore) {
            Matcher matcher = ONLINE.matcher(line);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(2)) - Integer.parseInt(matcher.group(1));
            }
        }
        return 0;
    }
}
