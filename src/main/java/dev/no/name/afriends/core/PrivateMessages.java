package dev.no.name.afriends.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Переписка одного аккаунта на одном сервере. Сам хранит только то, что связывает части вместе
 * (непрочитанное, открытый чат, «был в сети»), а данные держат части с одной обязанностью:
 * {@link MessageHistory}, {@link ChatOrganizer}, {@link PinnedMessages}, {@link GroupProfiles},
 * {@link DeliveryReceipts}, {@link Drafts}. Каждое изменение сообщает в {@link MessageEvents}
 */
public final class PrivateMessages {
    public static final String NOTES = ChatId.NOTES_KEY;
    public static final String OFFICIAL = ChatId.OFFICIAL_KEY;
    public static final String OFFICIAL_NAME = "AFriends";
    public static final String GROUP_PREFIX = ChatId.GROUP_PREFIX;
    public static final int RECEIPT_NONE = DeliveryReceipts.NONE;
    public static final int RECEIPT_DELIVERED = DeliveryReceipts.DELIVERED;
    public static final int RECEIPT_READ = DeliveryReceipts.READ;
    public static final int MAX_DRAFT = Drafts.MAX_DRAFT;

    private final MessageHistory history = new MessageHistory();
    private final ChatOrganizer organizer = new ChatOrganizer();
    private final PinnedMessages pins = new PinnedMessages();
    private final GroupProfiles groups = new GroupProfiles();
    private final DeliveryReceipts receipts = new DeliveryReceipts();
    private final Drafts drafts = new Drafts();
    private final Map<String, Integer> unread = new HashMap<>();
    private final Map<String, Long> offlineAt = new HashMap<>();
    private final MessageEvents events;
    // заглушённый чат остаётся в архиве и при новом сообщении, как в Telegram
    private final Predicate<String> staysArchived;
    private String openChat;

    public PrivateMessages() {
        this(MessageEvents.NONE, chat -> false);
    }

    // staysArchived — какие чаты не возвращаются из архива новым сообщением (заглушённые)
    public PrivateMessages(MessageEvents events, Predicate<String> staysArchived) {
        this.events = events;
        this.staysArchived = staysArchived;
    }

    // ---- сообщения ----

    public void add(String partner, PrivateMessage message) {
        ChatId chat = ChatId.of(partner);
        offlineAt.remove(chat.key());
        history.add(chat, message);
        if (!message.fromMe() && organizer.isArchived(chat.key()) && !staysArchived.test(partner)) {
            organizer.unarchive(chat.key());
        }
        if (!message.fromMe() && !chat.key().equals(openChat)) {
            unread.merge(chat.key(), 1, Integer::sum);
            events.incoming(partner);
        }
        events.changed();
    }

    public void addNote(String text, long now) {
        addNote(new PrivateMessage(true, text, now));
    }

    // заметка с картинками или пересланная (картинки лежат только в кеше у себя)
    public void addNote(PrivateMessage note) {
        history.add(ChatId.NOTES, note.id() == null ? note.withId(MessageIds.next()) : note);
        events.changed();
    }

    // реакция на сообщение по его id; false — такого сообщения здесь нет
    public boolean react(String partner, String messageId, boolean mine, String reaction) {
        return changed(history.replaceById(key(partner), messageId, message -> message.withReaction(mine, reaction)));
    }

    // голос в опросе группы: voter — ник участника, null — свой; choice -1 — отозван. false — такого опроса здесь нет
    public boolean vote(String chat, String messageId, String voter, int choice) {
        return changed(history.replaceById(key(chat), messageId, message -> {
            if (message.poll() == null) {
                return null;
            }
            GroupPoll poll = voter == null ? message.poll().withMine(choice) : message.poll().withVote(voter, choice);
            return message.withPoll(poll);
        }));
    }

    // сообщение по ключу (см. PrivateMessage.key); null — его здесь уже нет
    public PrivateMessage find(String partner, String messageKey) {
        return history.find(key(partner), messageKey);
    }

    // удалить сообщения по ключам (у себя или по просьбе собеседника); false — ни одного не нашлось
    public boolean remove(String partner, Collection<String> messageKeys) {
        if (!history.has(key(partner)) || messageKeys.isEmpty()) {
            return false;
        }
        boolean removed = history.remove(key(partner), messageKeys);
        pins.unpinAll(key(partner), messageKeys);
        return changed(removed);
    }

    public List<PrivateMessage> chat(String partner) {
        return history.chat(key(partner));
    }

    public PrivateMessage last(String partner) {
        return history.last(key(partner));
    }

    public boolean isEmpty() {
        return history.isEmpty();
    }

    // ---- закреплённые сообщения ----

    // закрепить или открепить сообщение; false — его здесь нет (при откреплении — всегда true)
    public boolean pin(String partner, String messageKey, boolean pinnedNow) {
        if (!pinnedNow) {
            pins.unpin(key(partner), messageKey);
        } else if (find(partner, messageKey) == null) {
            pins.unpin(key(partner), messageKey);
            return false;
        } else {
            pins.pin(key(partner), messageKey);
        }
        events.changed();
        return true;
    }

    public boolean isMessagePinned(String partner, String messageKey) {
        return pins.isPinned(key(partner), messageKey);
    }

    // закреплённые сообщения чата по порядку переписки; удалённые и обрезанные историей пропускаются
    public List<PrivateMessage> pinnedMessages(String partner) {
        List<String> chatPins = pins.keys(key(partner));
        if (chatPins.isEmpty()) {
            return List.of();
        }
        return history.chat(key(partner)).stream().filter(message -> chatPins.contains(message.key())).toList();
    }

    // ---- список чатов ----

    /** Чаты вне архива: сначала закреплённые, потом по времени последнего сообщения. */
    public List<String> partners() {
        return sorted(false);
    }

    /** Чаты в архиве, по времени последнего сообщения. */
    public List<String> archivedPartners() {
        return sorted(true);
    }

    private List<String> sorted(boolean inArchive) {
        List<String> keys = new ArrayList<>(history.keys());
        keys.remove(NOTES);
        keys.removeIf(key -> organizer.isArchived(key) != inArchive);
        keys.sort(Comparator.comparing((String key) -> !organizer.isPinned(key))
                .thenComparing(Comparator.comparingLong(history::lastTime).reversed()));
        // служебные чаты отдаются ключом: имя у них для показа, а не для поиска переписки
        return keys.stream().map(this::label).toList();
    }

    // есть ли такой чат у себя (может быть пустым — только что созданная группа)
    public boolean hasChat(String chat) {
        return history.has(key(chat));
    }

    // пустой чат в списке: новая группа появляется сразу, ещё без сообщений
    public void ensureChat(String chat) {
        changed(history.ensure(key(chat)));
    }

    public String displayName(String partner) {
        return switch (ChatId.of(partner)) {
            case ChatId.Official official -> OFFICIAL_NAME;
            case ChatId.Group group -> groups.title(partner);
            case ChatId.Notes notes -> partner;
            case ChatId.Direct direct -> history.name(direct.key(), partner);
        };
    }

    public boolean isArchived(String partner) {
        return partner != null && organizer.isArchived(key(partner));
    }

    // чат AFriends и «Избранное» в архив не убираются
    public void toggleArchived(String partner) {
        changed(organizer.toggleArchived(ChatId.of(partner)));
    }

    /** Непрочитанное в архиве — для значка у строки «Архив». */
    public int archivedUnread() {
        return organizer.archived().stream().mapToInt(key -> unread.getOrDefault(key, 0)).sum();
    }

    public boolean isPinned(String partner) {
        return organizer.isPinned(key(partner));
    }

    // чат AFriends не закрепляется: он стоит среди остальных по времени последнего сообщения
    public void togglePin(String partner) {
        changed(organizer.togglePin(ChatId.of(partner)));
    }

    public void clear(String partner) {
        history.clear(key(partner));
        pins.remove(key(partner));
        unread.remove(key(partner));
        events.changed();
    }

    public void delete(String partner) {
        history.delete(key(partner));
        unread.remove(key(partner));
        organizer.remove(key(partner));
        pins.remove(key(partner));
        drafts.remove(key(partner));
        groups.remove(partner);
        events.changed();
    }

    // ---- непрочитанное и открытый чат ----

    public void markOffline(String partner, long now) {
        offlineAt.put(key(partner), now);
    }

    public long offlineAt(String partner) {
        return offlineAt.getOrDefault(key(partner), 0L);
    }

    public boolean isOpen(String partner) {
        return key(partner).equals(openChat);
    }

    public void open(String partner) {
        openChat = key(partner);
        unread.remove(openChat);
    }

    public void close() {
        openChat = null;
    }

    public void markAllRead() {
        unread.clear();
    }

    public int unread(String partner) {
        return unread.getOrDefault(key(partner), 0);
    }

    public int totalUnread() {
        return unread.values().stream().mapToInt(Integer::intValue).sum();
    }

    // ---- группы ----

    public static String groupKey(String groupId) {
        return ChatId.group(groupId).key();
    }

    public static boolean isGroup(String chat) {
        return ChatId.of(chat) instanceof ChatId.Group;
    }

    // id группы из ключа чата или null
    public static String groupId(String chat) {
        return ChatId.of(chat) instanceof ChatId.Group group ? group.id() : null;
    }

    public static boolean isOfficial(String partner) {
        return ChatId.of(partner) instanceof ChatId.Official;
    }

    public void setTitle(String chat, String title) {
        groups.setTitle(chat, title);
        events.changed();
    }

    public boolean hasTitle(String chat) {
        return groups.hasTitle(chat);
    }

    public String description(String chat) {
        return groups.description(chat);
    }

    // пустое — убрать
    public void setDescription(String chat, String text) {
        groups.setDescription(chat, text);
        events.changed();
    }

    public MessageImage avatar(String chat) {
        return groups.avatar(chat);
    }

    public void setAvatar(String chat, MessageImage image) {
        groups.setAvatar(chat, image);
        events.changed();
    }

    // ---- отметки о доставке ----

    /** Отметка на своё сообщение: RECEIPT_NONE, RECEIPT_DELIVERED или RECEIPT_READ. */
    public int receipt(String messageId) {
        return receipts.state(messageId);
    }

    /** Отметка только повышается: «прочитано» не превратится обратно в «доставлено». false — ничего не поменялось. */
    public boolean markReceipt(String messageId, int state) {
        return changed(receipts.mark(messageId, state));
    }

    // ---- черновики ----

    public String draft(String partner) {
        return partner == null ? "" : drafts.get(key(partner));
    }

    // пустой текст убирает черновик
    public void setDraft(String partner, String text) {
        if (partner != null) {
            changed(drafts.set(key(partner), text));
        }
    }

    // ---- файл ----

    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        groups.writeTo(json);
        receipts.writeTo(json);
        drafts.writeTo(json, this::label);
        organizer.writeTo(json, this::label);
        pins.writeTo(json, this::label);
        history.writeTo(json, this::label);
        return json;
    }

    public void fromJson(JsonElement json) {
        history.clearAll();
        organizer.clear();
        pins.clear();
        drafts.clear();
        groups.clear();
        receipts.clear();
        if (!json.isJsonObject()) {
            return;
        }
        json.getAsJsonObject().entrySet().forEach(entry -> {
            String jsonKey = entry.getKey();
            JsonElement value = entry.getValue();
            if (ChatOrganizer.owns(jsonKey)) {
                organizer.read(jsonKey, value);
            } else if (PinnedMessages.JSON_KEY.equals(jsonKey)) {
                pins.read(value);
            } else if (GroupProfiles.owns(jsonKey)) {
                groups.read(jsonKey, value);
            } else if (DeliveryReceipts.JSON_KEY.equals(jsonKey)) {
                receipts.read(value);
            } else if (Drafts.JSON_KEY.equals(jsonKey)) {
                drafts.read(value);
            } else {
                history.read(jsonKey, value);
            }
        });
    }

    // под каким именем чат пишется в файл и отдаётся наружу: служебные — ключом, личка — ником
    private String label(String key) {
        return ChatId.isReserved(key) ? key : displayName(key);
    }

    private boolean changed(boolean changed) {
        if (changed) {
            events.changed();
        }
        return changed;
    }

    private static String key(String chat) {
        return ChatId.storageKey(chat);
    }
}
