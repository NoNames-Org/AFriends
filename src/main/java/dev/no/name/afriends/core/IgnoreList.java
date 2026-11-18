package dev.no.name.afriends.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public final class IgnoreList {
    private final Set<String> names = new TreeSet<>();

    public boolean isIgnored(String name) {
        return name != null && names.contains(key(name));
    }

    public boolean toggle(String name) {
        if (names.remove(key(name))) {
            return false;
        }
        names.add(key(name));
        return true;
    }

    public List<String> all() {
        return List.copyOf(names);
    }

    public JsonElement toJson() {
        JsonArray array = new JsonArray();
        names.forEach(array::add);
        return array;
    }

    public void fromJson(JsonElement json) {
        names.clear();
        if (json.isJsonArray()) {
            json.getAsJsonArray().forEach(element -> names.add(key(element.getAsString())));
        }
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
