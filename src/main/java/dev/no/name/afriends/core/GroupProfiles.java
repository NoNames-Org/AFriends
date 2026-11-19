package dev.no.name.afriends.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.no.name.afriends.AFriends;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Название, описание и аватарка групп. Сервер их не знает: названия хранятся только здесь,
 * описания и аватарки рассылают участники сквозными сообщениями. Ключ — ключ чата группы
 */
final class GroupProfiles {
    static final String TITLES_KEY = "$titles";
    static final String DESCRIPTIONS_KEY = "$gdesc";
    static final String AVATARS_KEY = "$gavatar";
    private static final String DEFAULT_TITLE = "Группа";
    private static final Logger LOGGER = LoggerFactory.getLogger(AFriends.NAME);

    private final Map<String, String> titles = new HashMap<>();
    private final Map<String, String> descriptions = new HashMap<>();
    private final Map<String, MessageImage> avatars = new HashMap<>();

    static boolean owns(String jsonKey) {
        return TITLES_KEY.equals(jsonKey) || DESCRIPTIONS_KEY.equals(jsonKey) || AVATARS_KEY.equals(jsonKey);
    }

    String title(String chat) {
        return titles.getOrDefault(chat, DEFAULT_TITLE);
    }

    boolean hasTitle(String chat) {
        return titles.containsKey(chat);
    }

    void setTitle(String chat, String title) {
        titles.put(chat, title);
    }

    String description(String chat) {
        return descriptions.getOrDefault(chat, "");
    }

    /** Пустое описание убирает его. */
    void setDescription(String chat, String text) {
        if (text == null || text.isBlank()) {
            descriptions.remove(chat);
        } else {
            descriptions.put(chat, text);
        }
    }

    MessageImage avatar(String chat) {
        return avatars.get(chat);
    }

    void setAvatar(String chat, MessageImage image) {
        if (image == null) {
            avatars.remove(chat);
        } else {
            avatars.put(chat, image);
        }
    }

    void remove(String chat) {
        titles.remove(chat);
        descriptions.remove(chat);
        avatars.remove(chat);
    }

    void clear() {
        titles.clear();
        descriptions.clear();
        avatars.clear();
    }

    void writeTo(JsonObject json) {
        JsonObject titlesJson = new JsonObject();
        titles.forEach(titlesJson::addProperty);
        json.add(TITLES_KEY, titlesJson);
        JsonObject descriptionsJson = new JsonObject();
        descriptions.forEach(descriptionsJson::addProperty);
        json.add(DESCRIPTIONS_KEY, descriptionsJson);
        JsonObject avatarsJson = new JsonObject();
        avatars.forEach((chat, image) -> avatarsJson.add(chat, image.toJson()));
        json.add(AVATARS_KEY, avatarsJson);
    }

    /** Читает одну из своих секций; битые записи пропускаются. Записи не про группы отбрасываются. */
    void read(String jsonKey, JsonElement json) {
        if (!json.isJsonObject()) {
            return;
        }
        json.getAsJsonObject().entrySet().forEach(entry -> {
            if (!PrivateMessages.isGroup(entry.getKey())) {
                return;
            }
            try {
                readEntry(jsonKey, entry.getKey(), entry.getValue());
            } catch (RuntimeException e) {
                LOGGER.warn("Пропущена битая запись {} в {}", entry.getKey(), jsonKey);
            }
        });
    }

    private void readEntry(String jsonKey, String chat, JsonElement value) {
        switch (jsonKey) {
            case TITLES_KEY -> {
                if (value.isJsonPrimitive()) {
                    titles.put(chat, value.getAsString());
                }
            }
            case DESCRIPTIONS_KEY -> descriptions.put(chat, value.getAsString());
            case AVATARS_KEY -> {
                MessageImage image = MessageImage.fromJson(value.getAsJsonObject());
                if (image != null) {
                    avatars.put(chat, image);
                }
            }
            default -> throw new IllegalArgumentException("Не секция групп: " + jsonKey);
        }
    }
}
