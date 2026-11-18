package dev.no.name.afriends.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.no.name.afriends.AFriends;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PrivateMessages {
    public static final String NOTES = "$notes";
    /** Личные сообщения от AFriends с портала. «$» не бывает в нике, поэтому игрок с ником AFriends сюда не попадёт. */
    public static final String OFFICIAL = "$blooddesigner";
    public static final String OFFICIAL_NAME = "AFriends";
    // групповой чат: «$g:» + id группы с сервера сети
    public static final String GROUP_PREFIX = "$g:";
    // названия групп: {ключ чата: название}. Сервер их не знает, хранятся только здесь
    private static final String TITLES_KEY = "$titles";
    // описания и аватарки групп: {ключ чата: текст / картинка}. Рассылают участники сквозными сообщениями
    private static final String DESCRIPTIONS_KEY = "$gdesc";
    private static final String AVATARS_KEY = "$gavatar";
    // отметки на свои сообщения: {id: 1 доставлено, 2 прочитано}; хранятся последние MAX_RECEIPTS
    private static final String RECEIPTS_KEY = "$receipts";
    private static final int MAX_RECEIPTS = 4000;
    public static final int RECEIPT_NONE = 0;
    public static final int RECEIPT_DELIVERED = 1;
    public static final int RECEIPT_READ = 2;
    private static final String PINNED_KEY = "$pinned";
    // чаты в архиве: [ключи]. Старые версии этот ключ пропустят как битую переписку
    private static final String ARCHIVED_KEY = "$archived";
    // закреплённые сообщения: {чат: [ключи]}. Старые версии этот ключ пропустят как битую переписку
    private static final String PINS_KEY = "$pins";
    // недописанные сообщения: {чат: текст}. Остаются после закрытия окна и игры, как черновики в Telegram
    private static final String DRAFTS_KEY = "$drafts";
    public static final int MAX_DRAFT = 1000;
    private static final Logger LOGGER = LoggerFactory.getLogger(AFriends.NAME);

    private static final Pattern PM = Pattern.compile(
            "^\\s*\\[(?:PM|ЛС)]\\s*([A-Za-z0-9_]{1,16})\\s*(?:→|->|»|>)\\s*([A-Za-z0-9_]{1,16})\\s*:\\s?(.*)$");

    public record Parsed(String partner, PrivateMessage message) {
    }

    private final Map<String, List<PrivateMessage>> chats = new HashMap<>();

    private final Map<String, String> names = new HashMap<>();
    private final Set<String> pinned = new HashSet<>();
    private final Set<String> archived = new HashSet<>();
    private final Map<String, List<String>> pins = new HashMap<>();
    private final Map<String, Integer> unread = new HashMap<>();
    private final Map<String, String> drafts = new HashMap<>();
    private final Map<String, String> titles = new HashMap<>();
    private final Map<String, String> descriptions = new HashMap<>();
    private final Map<String, MessageImage> avatars = new HashMap<>();
    private final Map<String, Integer> receipts = new LinkedHashMap<>();

    private final Map<String, Long> offlineAt = new HashMap<>();

    private String openChat;

    private Runnable onChange = () -> { };
    // заглушённый чат остаётся в архиве и при новом сообщении, как в Telegram
    private Predicate<String> staysArchived = chat -> false;

    public void setOnChange(Runnable listener) {
        onChange = listener;
    }

    public void setStaysArchived(Predicate<String> rule) {
        staysArchived = rule;
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
        if (!message.fromMe() && archived.contains(key(partner)) && !staysArchived.test(partner)) {
            archived.remove(key(partner));
        }
        if (!message.fromMe() && !key(partner).equals(openChat)) {
            unread.merge(key(partner), 1, Integer::sum);
            IncomingAlerts.fire(partner);
        }
        onChange.run();
    }

    // реакция на сообщение по его id; false — такого сообщения здесь нет
    public boolean react(String partner, String messageId, boolean mine, String reaction) {
        List<PrivateMessage> chat = chats.get(key(partner));
        if (chat == null || messageId == null) {
            return false;
        }
        for (int i = chat.size() - 1; i >= 0; i--) {
            if (messageId.equals(chat.get(i).id())) {
                chat.set(i, chat.get(i).withReaction(mine, reaction));
                onChange.run();
                return true;
            }
        }
        return false;
    }

    // голос в опросе группы: voter — ник участника, null — свой; choice -1 — отозван. false — такого опроса здесь нет
    public boolean vote(String chat, String messageId, String voter, int choice) {
        List<PrivateMessage> messages = chats.get(key(chat));
        if (messages == null || messageId == null) {
            return false;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            PrivateMessage message = messages.get(i);
            if (messageId.equals(message.id()) && message.poll() != null) {
                GroupPoll poll = voter == null ? message.poll().withMine(choice) : message.poll().withVote(voter, choice);
                messages.set(i, message.withPoll(poll));
                onChange.run();
                return true;
            }
        }
        return false;
    }

    // сообщение по ключу (см. PrivateMessage.key); null — его здесь уже нет
    public PrivateMessage find(String partner, String messageKey) {
        List<PrivateMessage> chat = chats.get(key(partner));
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

    // удалить сообщения по ключам (у себя или по просьбе собеседника); false — ни одного не нашлось
    public boolean remove(String partner, Collection<String> messageKeys) {
        List<PrivateMessage> chat = chats.get(key(partner));
        if (chat == null || messageKeys.isEmpty()) {
            return false;
        }
        boolean removed = chat.removeIf(message -> messageKeys.contains(message.key()));
        List<String> chatPins = pins.get(key(partner));
        if (chatPins != null) {
            chatPins.removeAll(messageKeys);
        }
        if (removed) {
            onChange.run();
        }
        return removed;
    }

    // закрепить или открепить сообщение; false — его здесь нет (при откреплении — всегда true)
    public boolean pin(String partner, String messageKey, boolean pinnedNow) {
        List<String> chatPins = pins.computeIfAbsent(key(partner), name -> new ArrayList<>());
        chatPins.remove(messageKey);
        if (pinnedNow) {
            if (find(partner, messageKey) == null) {
                return false;
            }
            chatPins.add(messageKey);
        }
        onChange.run();
        return true;
    }

    public boolean isMessagePinned(String partner, String messageKey) {
        return pins.getOrDefault(key(partner), List.of()).contains(messageKey);
    }

    // закреплённые сообщения чата по порядку переписки; удалённые и обрезанные историей пропускаются
    public List<PrivateMessage> pinnedMessages(String partner) {
        List<String> chatPins = pins.getOrDefault(key(partner), List.of());
        if (chatPins.isEmpty()) {
            return List.of();
        }
        return chats.getOrDefault(key(partner), List.of()).stream().filter(message -> chatPins.contains(message.key())).toList();
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
        addNote(new PrivateMessage(true, text, now));
    }

    // заметка с картинками или пересланная (картинки лежат только в кеше у себя)
    public void addNote(PrivateMessage note) {
        List<PrivateMessage> notes = chats.computeIfAbsent(NOTES, name -> new ArrayList<>());
        notes.add(note.id() == null ? note.withId(MessageIds.next()) : note);
        onChange.run();
    }

    /** Чаты вне архива: сначала закреплённые, потом по времени последнего сообщения. */
    public List<String> partners() {
        return sorted(false);
    }

    /** Чаты в архиве, по времени последнего сообщения. */
    public List<String> archivedPartners() {
        return sorted(true);
    }

    private List<String> sorted(boolean inArchive) {
        List<String> keys = new ArrayList<>(chats.keySet());
        keys.remove(NOTES);
        keys.removeIf(key -> archived.contains(key) != inArchive);
        keys.sort(Comparator.comparing((String key) -> !pinned.contains(key))
                .thenComparing(Comparator.comparingLong(this::lastTime).reversed()));
        // служебные чаты отдаются ключом: имя у них для показа, а не для поиска переписки
        return keys.stream().map(key -> isReserved(key) ? key : displayName(key)).toList();
    }

    public static String groupKey(String groupId) {
        return GROUP_PREFIX + groupId;
    }

    public static boolean isGroup(String chat) {
        return chat != null && chat.startsWith(GROUP_PREFIX);
    }

    // id группы из ключа чата или null
    public static String groupId(String chat) {
        return isGroup(chat) ? chat.substring(GROUP_PREFIX.length()) : null;
    }

    public void setTitle(String chat, String title) {
        titles.put(chat, title);
        onChange.run();
    }

    public boolean hasTitle(String chat) {
        return titles.containsKey(chat);
    }

    public String description(String chat) {
        return descriptions.getOrDefault(chat, "");
    }

    // пустое — убрать
    public void setDescription(String chat, String text) {
        if (text == null || text.isBlank()) {
            descriptions.remove(chat);
        } else {
            descriptions.put(chat, text);
        }
        onChange.run();
    }

    public MessageImage avatar(String chat) {
        return avatars.get(chat);
    }

    public void setAvatar(String chat, MessageImage image) {
        if (image == null) {
            avatars.remove(chat);
        } else {
            avatars.put(chat, image);
        }
        onChange.run();
    }

    /** Отметка на своё сообщение: RECEIPT_NONE, RECEIPT_DELIVERED или RECEIPT_READ. */
    public int receipt(String messageId) {
        return messageId == null ? RECEIPT_NONE : receipts.getOrDefault(messageId, RECEIPT_NONE);
    }

    /** Отметка только повышается: «прочитано» не превратится обратно в «доставлено». false — ничего не поменялось. */
    public boolean markReceipt(String messageId, int state) {
        if (messageId == null || state <= receipt(messageId)) {
            return false;
        }
        receipts.remove(messageId);
        receipts.put(messageId, state);
        while (receipts.size() > MAX_RECEIPTS) {
            receipts.remove(receipts.keySet().iterator().next());
        }
        onChange.run();
        return true;
    }

    // есть ли такой чат у себя (может быть пустым — только что созданная группа)
    public boolean hasChat(String chat) {
        return chats.containsKey(key(chat));
    }

    // пустой чат в списке: новая группа появляется сразу, ещё без сообщений
    public void ensureChat(String chat) {
        if (chats.putIfAbsent(key(chat), new ArrayList<>()) == null) {
            onChange.run();
        }
    }

    public static boolean isOfficial(String partner) {
        return OFFICIAL.equals(partner);
    }

    public String displayName(String partner) {
        if (isOfficial(partner)) {
            return OFFICIAL_NAME;
        }
        if (isGroup(partner)) {
            return titles.getOrDefault(partner, "Группа");
        }
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

    public boolean isArchived(String partner) {
        return partner != null && archived.contains(key(partner));
    }

    // чат AFriends и «Избранное» в архив не убираются
    public void toggleArchived(String partner) {
        if (isOfficial(partner) || NOTES.equals(partner)) {
            return;
        }
        if (!archived.remove(key(partner))) {
            archived.add(key(partner));
            pinned.remove(key(partner));
        }
        onChange.run();
    }

    /** Непрочитанное в архиве — для значка у строки «Архив». */
    public int archivedUnread() {
        return archived.stream().mapToInt(key -> unread.getOrDefault(key, 0)).sum();
    }

    public boolean isPinned(String partner) {
        return pinned.contains(key(partner));
    }

    // чат AFriends не закрепляется: он стоит среди остальных по времени последнего сообщения
    public void togglePin(String partner) {
        if (isOfficial(partner)) {
            return;
        }
        if (!pinned.remove(key(partner))) {
            pinned.add(key(partner));
            archived.remove(key(partner));
        }
        onChange.run();
    }

    public void clear(String partner) {
        chats.put(key(partner), new ArrayList<>());
        pins.remove(key(partner));
        unread.remove(key(partner));
        onChange.run();
    }

    public void delete(String partner) {
        chats.remove(key(partner));
        unread.remove(key(partner));
        pinned.remove(key(partner));
        archived.remove(key(partner));
        pins.remove(key(partner));
        drafts.remove(key(partner));
        titles.remove(partner);
        descriptions.remove(partner);
        avatars.remove(partner);
        onChange.run();
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

    public String draft(String partner) {
        return partner == null ? "" : drafts.getOrDefault(key(partner), "");
    }

    // пустой текст убирает черновик
    public void setDraft(String partner, String text) {
        if (partner == null) {
            return;
        }
        String clean = text == null ? "" : text.length() > MAX_DRAFT ? text.substring(0, MAX_DRAFT) : text;
        String previous = clean.isBlank() ? drafts.remove(key(partner)) : drafts.put(key(partner), clean);
        if (!clean.isBlank() || previous != null) {
            onChange.run();
        }
    }

    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        JsonObject titlesJson = new JsonObject();
        titles.forEach(titlesJson::addProperty);
        json.add(TITLES_KEY, titlesJson);
        JsonObject descriptionsJson = new JsonObject();
        descriptions.forEach(descriptionsJson::addProperty);
        json.add(DESCRIPTIONS_KEY, descriptionsJson);
        JsonObject avatarsJson = new JsonObject();
        avatars.forEach((chat, image) -> avatarsJson.add(chat, image.toJson()));
        json.add(AVATARS_KEY, avatarsJson);
        JsonObject receiptsJson = new JsonObject();
        receipts.forEach(receiptsJson::addProperty);
        json.add(RECEIPTS_KEY, receiptsJson);
        JsonObject draftsJson = new JsonObject();
        drafts.forEach((key, text) -> draftsJson.addProperty(isReserved(key) ? key : displayName(key), text));
        json.add(DRAFTS_KEY, draftsJson);
        JsonArray pinnedArray = new JsonArray();
        pinned.forEach(name -> pinnedArray.add(isReserved(name) ? name : displayName(name)));
        json.add(PINNED_KEY, pinnedArray);
        JsonArray archivedArray = new JsonArray();
        archived.forEach(name -> archivedArray.add(isReserved(name) ? name : displayName(name)));
        json.add(ARCHIVED_KEY, archivedArray);
        JsonObject pinsJson = new JsonObject();
        pins.forEach((key, keys) -> {
            if (!keys.isEmpty()) {
                JsonArray array = new JsonArray();
                keys.forEach(array::add);
                pinsJson.add(isReserved(key) ? key : displayName(key), array);
            }
        });
        json.add(PINS_KEY, pinsJson);
        chats.forEach((key, messages) -> {
            String partner = isReserved(key) ? key : displayName(key);
            JsonArray array = new JsonArray();
            for (PrivateMessage message : messages) {
                array.add(MessageJson.write(message));
            }
            json.add(partner, array);
        });
        return json;
    }

    public void fromJson(JsonElement json) {
        chats.clear();
        names.clear();
        pinned.clear();
        archived.clear();
        pins.clear();
        drafts.clear();
        titles.clear();
        descriptions.clear();
        avatars.clear();
        receipts.clear();
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
            if (ARCHIVED_KEY.equals(entry.getKey())) {
                if (entry.getValue().isJsonArray()) {
                    entry.getValue().getAsJsonArray().forEach(name -> archived.add(key(name.getAsString())));
                }
                return;
            }
            if (PINS_KEY.equals(entry.getKey())) {
                readPins(entry.getValue());
                return;
            }
            if (TITLES_KEY.equals(entry.getKey())) {
                if (entry.getValue().isJsonObject()) {
                    entry.getValue().getAsJsonObject().entrySet().forEach(title -> {
                        if (isGroup(title.getKey()) && title.getValue().isJsonPrimitive()) {
                            titles.put(title.getKey(), title.getValue().getAsString());
                        }
                    });
                }
                return;
            }
            if (DESCRIPTIONS_KEY.equals(entry.getKey()) || AVATARS_KEY.equals(entry.getKey())
                    || RECEIPTS_KEY.equals(entry.getKey())) {
                readChatInfo(entry.getKey(), entry.getValue());
                return;
            }
            if (DRAFTS_KEY.equals(entry.getKey())) {
                if (entry.getValue().isJsonObject()) {
                    entry.getValue().getAsJsonObject().entrySet().forEach(draft -> {
                        if (draft.getValue().isJsonPrimitive()) {
                            setDraft(draft.getKey(), draft.getValue().getAsString());
                        }
                    });
                }
                return;
            }
            try {
                List<PrivateMessage> messages = new ArrayList<>();
                for (JsonElement element : entry.getValue().getAsJsonArray()) {
                    messages.add(MessageJson.read(element.getAsJsonObject()));
                }
                String partner = entry.getKey();
                if (NOTES.equals(partner)) {
                    // заметки старых версий без id: выдаём, чтобы закреп, ответ и удаление их различали
                    messages.replaceAll(note -> note.id() == null ? note.withId(MessageIds.next()) : note);
                }
                if (isReserved(partner)) {
                    chats.put(partner, messages);
                } else {
                    chats.put(key(partner), messages);
                    names.put(key(partner), partner);
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Пропущена битая переписка с {}", entry.getKey(), e);
            }
        });
    }

    private void readChatInfo(String kind, JsonElement json) {
        if (!json.isJsonObject()) {
            return;
        }
        json.getAsJsonObject().entrySet().forEach(entry -> {
            try {
                switch (kind) {
                    case DESCRIPTIONS_KEY -> {
                        if (isGroup(entry.getKey())) {
                            descriptions.put(entry.getKey(), entry.getValue().getAsString());
                        }
                    }
                    case AVATARS_KEY -> {
                        MessageImage image = isGroup(entry.getKey()) ? MessageImage.fromJson(entry.getValue().getAsJsonObject()) : null;
                        if (image != null) {
                            avatars.put(entry.getKey(), image);
                        }
                    }
                    default -> receipts.put(entry.getKey(), entry.getValue().getAsInt());
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Пропущена битая запись {} в {}", entry.getKey(), kind);
            }
        });
    }

    private void readPins(JsonElement json) {
        if (!json.isJsonObject()) {
            return;
        }
        json.getAsJsonObject().entrySet().forEach(entry -> {
            if (entry.getValue().isJsonArray()) {
                List<String> keys = new ArrayList<>();
                entry.getValue().getAsJsonArray().forEach(item -> keys.add(item.getAsString()));
                pins.put(key(entry.getKey()), keys);
            }
        });
    }

    private static String key(String name) {
        return isReserved(name) ? name : name.toLowerCase(Locale.ROOT);
    }

    // служебные чаты: их ключ не ник и хранится как есть
    private static boolean isReserved(String name) {
        return NOTES.equals(name) || OFFICIAL.equals(name) || isGroup(name);
    }
}
