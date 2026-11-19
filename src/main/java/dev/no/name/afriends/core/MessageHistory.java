package dev.no.name.afriends.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.no.name.afriends.AFriends;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Сами сообщения по чатам и ники собеседников в исходном регистре. Ключ — ключ хранения ({@link ChatId#key()}).
 * Ничего не знает про архив, закрепы и непрочитанное: это забота {@link PrivateMessages}
 */
final class MessageHistory {
    private static final Logger LOGGER = LoggerFactory.getLogger(AFriends.NAME);

    private final Map<String, List<PrivateMessage>> chats = new HashMap<>();
    // ключ → ник, как он пришёл: «bob» → «Bob»
    private final Map<String, String> names = new HashMap<>();

    void add(ChatId chat, PrivateMessage message) {
        if (chat instanceof ChatId.Direct direct) {
            names.put(chat.key(), direct.name());
        }
        chats.computeIfAbsent(chat.key(), key -> new ArrayList<>()).add(message);
    }

    /** Заменить последнее сообщение с этим id; false — такого нет. */
    boolean replaceById(String key, String messageId, UnaryOperator<PrivateMessage> change) {
        List<PrivateMessage> chat = chats.get(key);
        if (chat == null || messageId == null) {
            return false;
        }
        for (int i = chat.size() - 1; i >= 0; i--) {
            if (messageId.equals(chat.get(i).id())) {
                PrivateMessage changed = change.apply(chat.get(i));
                if (changed == null) {
                    return false;
                }
                chat.set(i, changed);
                return true;
            }
        }
        return false;
    }

    PrivateMessage find(String key, String messageKey) {
        List<PrivateMessage> chat = chats.get(key);
        if (chat == null || messageKey == null) {
            return null;
        }
        for (int i = chat.size() - 1; i >= 0; i--) {
            if (messageKey.equals(chat.get(i).key())) {
                return chat.get(i);
            }
        }
        return null;
    }

    boolean remove(String key, Collection<String> messageKeys) {
        List<PrivateMessage> chat = chats.get(key);
        return chat != null && chat.removeIf(message -> messageKeys.contains(message.key()));
    }

    List<PrivateMessage> chat(String key) {
        return List.copyOf(chats.getOrDefault(key, List.of()));
    }

    PrivateMessage last(String key) {
        List<PrivateMessage> chat = chats.get(key);
        return chat == null || chat.isEmpty() ? null : chat.get(chat.size() - 1);
    }

    long lastTime(String key) {
        PrivateMessage message = last(key);
        return message == null ? 0 : message.time();
    }

    // в порядке HashMap, как до разделения: при равном времени чаты не перемешиваются между запусками
    List<String> keys() {
        return new ArrayList<>(chats.keySet());
    }

    boolean has(String key) {
        return chats.containsKey(key);
    }

    /** true — чат создан. */
    boolean ensure(String key) {
        return chats.putIfAbsent(key, new ArrayList<>()) == null;
    }

    void clear(String key) {
        chats.put(key, new ArrayList<>());
    }

    void delete(String key) {
        chats.remove(key);
    }

    boolean isEmpty() {
        return chats.values().stream().allMatch(List::isEmpty);
    }

    /** Ник как он пришёл; если не знаем — переданная строка. */
    String name(String key, String fallback) {
        return names.getOrDefault(key, fallback);
    }

    void clearAll() {
        chats.clear();
        names.clear();
    }

    void writeTo(JsonObject json, UnaryOperator<String> label) {
        chats.forEach((key, messages) -> {
            JsonArray array = new JsonArray();
            for (PrivateMessage message : messages) {
                array.add(MessageJson.write(message));
            }
            json.add(label.apply(key), array);
        });
    }

    /** Переписка одного чата из файла; битая пропускается целиком. */
    void read(String partner, JsonElement json) {
        try {
            List<PrivateMessage> messages = new ArrayList<>();
            for (JsonElement element : json.getAsJsonArray()) {
                messages.add(MessageJson.read(element.getAsJsonObject()));
            }
            ChatId chat = ChatId.of(partner);
            if (chat instanceof ChatId.Notes) {
                // заметки старых версий без id: выдаём, чтобы закреп, ответ и удаление их различали
                messages.replaceAll(note -> note.id() == null ? note.withId(MessageIds.next()) : note);
            }
            chats.put(chat.key(), messages);
            if (chat instanceof ChatId.Direct) {
                names.put(chat.key(), partner);
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Пропущена битая переписка с {}", partner, e);
        }
    }
}
