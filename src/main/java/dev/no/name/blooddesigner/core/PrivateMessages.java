package dev.no.name.blooddesigner.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.no.name.blooddesigner.BloodDesigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PrivateMessages {
    public static final String NOTES = "$notes";
    private static final String PINNED_KEY = "$pinned";
    private static final Logger LOGGER = LoggerFactory.getLogger(BloodDesigner.NAME);

    private static final Pattern PM = Pattern.compile(
            "^\\s*\\[(?:PM|ЛС)]\\s*([A-Za-z0-9_]{1,16})\\s*(?:→|->|»|>)\\s*([A-Za-z0-9_]{1,16})\\s*:\\s?(.*)$");

    public record Parsed(String partner, PrivateMessage message) {
    }

    private final Map<String, List<PrivateMessage>> chats = new HashMap<>();

    private final Map<String, String> names = new HashMap<>();
    private final Set<String> pinned = new HashSet<>();
    private final Map<String, Integer> unread = new HashMap<>();

    private final Map<String, Long> offlineAt = new HashMap<>();

    private String openChat;

    private Runnable onChange = () -> { };

    public void setOnChange(Runnable listener) {
        onChange = listener;
    }

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

    public void add(String partner, PrivateMessage message) {
        offlineAt.remove(key(partner));
        names.put(key(partner), partner);
        List<PrivateMessage> chat = chats.computeIfAbsent(key(partner), name -> new ArrayList<>());
        chat.add(message);
        trim(chat);
        if (!message.fromMe() && !key(partner).equals(openChat)) {
            unread.merge(key(partner), 1, Integer::sum);
        }
        onChange.run();
    }

    public void markOffline(String partner, long now) {
        offlineAt.put(key(partner), now);
    }

    public long offlineAt(String partner) {
        return offlineAt.getOrDefault(key(partner), 0L);
    }

    public boolean isOpen(String partner) {
        return key(partner).equals(openChat);
    }

    public void addNote(String text, long now) {
        List<PrivateMessage> notes = chats.computeIfAbsent(NOTES, name -> new ArrayList<>());
        notes.add(new PrivateMessage(true, text, now));
        trim(notes);
        onChange.run();
    }

    public List<String> partners() {
        List<String> keys = new ArrayList<>(chats.keySet());
        keys.remove(NOTES);
        keys.sort(Comparator.comparing((String key) -> !pinned.contains(key))
                .thenComparing(Comparator.comparingLong(this::lastTime).reversed()));
        return keys.stream().map(this::displayName).toList();
    }

    public String displayName(String partner) {
        return names.getOrDefault(key(partner), partner);
    }

    public PrivateMessage last(String partner) {
        List<PrivateMessage> chat = chats.get(key(partner));
        return chat == null || chat.isEmpty() ? null : chat.get(chat.size() - 1);
    }

    private long lastTime(String key) {
        PrivateMessage message = last(key);
        return message == null ? 0 : message.time();
    }

    public boolean isPinned(String partner) {
        return pinned.contains(key(partner));
    }

    public void togglePin(String partner) {
        if (!pinned.remove(key(partner))) {
            pinned.add(key(partner));
        }
        onChange.run();
    }

    public void clear(String partner) {
        chats.put(key(partner), new ArrayList<>());
        unread.remove(key(partner));
        onChange.run();
    }

    public void delete(String partner) {
        chats.remove(key(partner));
        unread.remove(key(partner));
        pinned.remove(key(partner));
        onChange.run();
    }

    public void trimAll() {
        chats.values().forEach(PrivateMessages::trim);
        onChange.run();
    }

    private static void trim(List<PrivateMessage> chat) {
        int extra = chat.size() - MessageHistory.limit();
        if (extra > 0) {
            chat.subList(0, extra).clear();
        }
    }

    public void markAllRead() {
        unread.clear();
    }

    public List<PrivateMessage> chat(String partner) {
        return List.copyOf(chats.getOrDefault(key(partner), List.of()));
    }

    public int unread(String partner) {
        return unread.getOrDefault(key(partner), 0);
    }

    public boolean isEmpty() {
        return chats.values().stream().allMatch(List::isEmpty);
    }

    public int totalUnread() {
        return unread.values().stream().mapToInt(Integer::intValue).sum();
    }

    public void open(String partner) {
        openChat = key(partner);
        unread.remove(openChat);
    }

    public void close() {
        openChat = null;
    }

    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        JsonArray pinnedArray = new JsonArray();
        pinned.forEach(name -> pinnedArray.add(displayName(name)));
        json.add(PINNED_KEY, pinnedArray);
        chats.forEach((key, messages) -> {
            String partner = NOTES.equals(key) ? NOTES : displayName(key);
            JsonArray array = new JsonArray();
            int from = Math.max(0, messages.size() - MessageHistory.limit());
            for (PrivateMessage message : messages.subList(from, messages.size())) {
                JsonObject item = new JsonObject();
                item.addProperty("me", message.fromMe());
                item.addProperty("text", message.text());
                item.addProperty("time", message.time());
                array.add(item);
            }
            json.add(partner, array);
        });
        return json;
    }

    public void fromJson(JsonElement json) {
        chats.clear();
        names.clear();
        pinned.clear();
        if (!json.isJsonObject()) {
            return;
        }
        json.getAsJsonObject().entrySet().forEach(entry -> {
            if (PINNED_KEY.equals(entry.getKey())) {
                if (entry.getValue().isJsonArray()) {
                    entry.getValue().getAsJsonArray().forEach(name -> pinned.add(key(name.getAsString())));
                }
                return;
            }
            try {
                List<PrivateMessage> messages = new ArrayList<>();
                for (JsonElement element : entry.getValue().getAsJsonArray()) {
                    JsonObject item = element.getAsJsonObject();
                    messages.add(new PrivateMessage(item.get("me").getAsBoolean(), item.get("text").getAsString(),
                            item.get("time").getAsLong()));
                }
                trim(messages);
                String partner = entry.getKey();
                if (NOTES.equals(partner)) {
                    chats.put(NOTES, messages);
                } else {
                    chats.put(key(partner), messages);
                    names.put(key(partner), partner);
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Пропущена битая переписка с {}", entry.getKey(), e);
            }
        });
    }

    private static String key(String name) {
        return NOTES.equals(name) ? NOTES : name.toLowerCase(Locale.ROOT);
    }
}
