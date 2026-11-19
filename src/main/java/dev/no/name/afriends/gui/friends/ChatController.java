package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.core.GroupChats;
import dev.no.name.afriends.core.MessageReply;
import dev.no.name.afriends.core.MessageRoute;
import dev.no.name.afriends.core.NotifySettings;
import dev.no.name.afriends.core.PrivateMessage;
import dev.no.name.afriends.core.PrivateMessages;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Открытый чат без отрисовки: какой чат выбран, что ещё отправляется, сколько истории разложено в ленте,
 * подпись-предупреждение над полем ввода и отправка текста, голосовых и опросов. Окно только рисует это состояние
 */
final class ChatController {
    /** Чат «Объявления»: всегда первый, только для чтения, не очищается и не удаляется. Пишут с portal.no-names.com. */
    static final String ANNOUNCEMENTS = "$announcements";
    static final String ANNOUNCEMENTS_TITLE = "Объявления";
    static final String NOTES_TITLE = "Избранное";
    // сколько последних сообщений чата отмечаются прочитанными при открытии
    static final int READ_WINDOW = 50;
    // лента раскладывает не всю историю, а последние сообщения: столько сразу и столько ещё при прокрутке к верху
    static final int HISTORY_PAGE = 80;
    static final long WARNING_MS = 4000;
    static final long PENDING_TIMEOUT_MS = 6000;
    private static final long ANNOUNCEMENTS_SINCE = LocalDate.of(2026, 11, 16)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    /** Пока с сервера ничего не пришло. */
    private static final List<PrivateMessage> ANNOUNCEMENTS_EMPTY = List.of(new PrivateMessage(false,
            "Здесь будут новости от AFriends: обновления мода и важные изменения. Писать сюда нельзя.",
            ANNOUNCEMENTS_SINCE));

    /** Своё сообщение, которое ушло, но ещё не вернулось в переписку. */
    record Pending(String partner, String text, long sentAt, MessageReply reply) {
    }

    /** Звук на предупреждение и на отправку: окно играет его, контроллер о звуках не знает. */
    interface Feedback {
        void warned();

        void sent();
    }

    private final PrivateMessages messages;
    private final MessageRoute route;
    private final NotifySettings alerts;
    private final Supplier<List<PrivateMessage>> announcements;
    private final Feedback feedback;
    private final LongSupplier clock;
    private final List<Pending> pending = new ArrayList<>();
    // когда сообщение появилось в ленте (по индексу в переписке): для анимации появления
    private final Map<Integer, Long> appearAt = new HashMap<>();
    private String partner;
    // список показывает архив вместо обычных чатов
    private boolean archiveView;
    private int shownCount;
    // сколько последних сообщений чата лента раскладывает сейчас; растёт порциями при прокрутке вверх и переходе к старому
    private int historyWindow = HISTORY_PAGE;
    // к какому сообщению прокрутить на следующем кадре
    private String jumpKey;
    // почему сообщение не ушло: подпись над полем ввода
    private String warning = "";
    private long warningAt;

    ChatController(PrivateMessages messages, MessageRoute route, NotifySettings alerts,
                   Supplier<List<PrivateMessage>> announcements, Feedback feedback, LongSupplier clock) {
        this.messages = messages;
        this.route = route;
        this.alerts = alerts;
        this.announcements = announcements;
        this.feedback = feedback;
        this.clock = clock;
    }

    // ---- выбранный чат ----

    /** Открыть чат: черновик уходящего сохраняется; вернёт черновик нового. */
    String select(String chat, String leavingDraft) {
        messages.close();
        messages.setDraft(partner, leavingDraft);
        partner = chat;
        pending.clear();
        appearAt.clear();
        historyWindow = HISTORY_PAGE;
        jumpKey = null;
        if (chat != null) {
            if (!ANNOUNCEMENTS.equals(chat)) {
                messages.open(chat);
                markRead(messages.chat(chat));
            }
            shownCount = chatMessages(chat).size();
        }
        return messages.draft(chat);
    }

    /** Окно закрывается: черновик остаётся до следующего раза. */
    void leave(String draft) {
        messages.setDraft(partner, draft);
        messages.close();
    }

    String partner() {
        return partner;
    }

    boolean isSelected(String chat) {
        return chat.equalsIgnoreCase(partner == null ? "" : partner);
    }

    boolean archiveView() {
        return archiveView;
    }

    void setArchiveView(boolean archive) {
        archiveView = archive;
    }

    boolean isAnnouncements() {
        return ANNOUNCEMENTS.equals(partner);
    }

    boolean isOfficial() {
        return PrivateMessages.isOfficial(partner);
    }

    boolean isNotes() {
        return PrivateMessages.NOTES.equals(partner);
    }

    boolean isGroup() {
        return PrivateMessages.isGroup(partner);
    }

    // сюда писать нельзя: объявления
    boolean isReadOnly() {
        return isAnnouncements();
    }

    GroupChats groups() {
        return route.groups();
    }

    List<PrivateMessage> chatMessages(String chat) {
        return ANNOUNCEMENTS.equals(chat) ? announcementList() : messages.chat(chat);
    }

    PrivateMessage lastMessage(String entry) {
        return ANNOUNCEMENTS.equals(entry) ? announcementList().getLast() : messages.last(entry);
    }

    List<PrivateMessage> pinnedMessages() {
        return partner == null || isAnnouncements() ? List.of() : messages.pinnedMessages(partner);
    }

    private List<PrivateMessage> announcementList() {
        List<PrivateMessage> loaded = announcements.get();
        return loaded.isEmpty() ? ANNOUNCEMENTS_EMPTY : loaded;
    }

    // собеседникам по сети — «прочитано» на то, что игрок сейчас видит (если он не выключил это в настройках)
    void markRead(List<PrivateMessage> seen) {
        if (partner != null && !isAnnouncements() && alerts.readReceipts() && !seen.isEmpty()) {
            int from = Math.max(0, seen.size() - READ_WINDOW);
            route.markRead(partner, seen.subList(from, seen.size()));
        }
    }

    // ---- лента: новые сообщения, история, переход ----

    /**
     * Сверить переписку с тем, что лента уже показала: новые сообщения получают время появления, а свои — время
     * отправки из ожидающих. true — что-то пришло или ушло
     */
    boolean trackNewMessages(List<PrivateMessage> chat) {
        if (chat.size() == shownCount) {
            return false;
        }
        if (chat.size() > shownCount) {
            markRead(chat.subList(shownCount, chat.size()));
        }
        long now = clock.getAsLong();
        for (int i = shownCount; i < chat.size(); i++) {
            appearAt.put(i, claimPending(chat.get(i), now));
        }
        // новые сообщения не выталкивают из ленты старые, уже загруженные
        if (chat.size() > shownCount) {
            historyWindow += chat.size() - shownCount;
        }
        shownCount = chat.size();
        return true;
    }

    // своё сообщение вернулось из сети: ожидающее с тем же текстом уходит, анимация продолжается с момента отправки
    private long claimPending(PrivateMessage message, long now) {
        if (!message.fromMe()) {
            return now;
        }
        Pending match = pending.stream()
                .filter(item -> item.partner().equalsIgnoreCase(partner) && item.text().equals(message.text().strip()))
                .findFirst().orElse(null);
        if (match == null) {
            return now;
        }
        pending.remove(match);
        return match.sentAt();
    }

    /** Когда сообщение с этим индексом в переписке появилось; 0 — было до открытия. */
    long appearStart(int index) {
        return appearAt.getOrDefault(index, 0L);
    }

    /** С какого сообщения переписки лента раскладывает историю. */
    int historyFrom(int chatSize) {
        return Math.max(0, chatSize - historyWindow);
    }

    int historyWindow() {
        return historyWindow;
    }

    /** Докрутили почти до верха: следующая порция истории. */
    void loadOlder() {
        historyWindow += HISTORY_PAGE;
    }

    void requestJump(String messageKey) {
        jumpKey = messageKey;
    }

    String jumpKey() {
        return jumpKey;
    }

    void clearJump() {
        jumpKey = null;
    }

    // расширить загруженную часть так, чтобы сообщение key в неё попало (с запасом сверху)
    void revealForJump(List<PrivateMessage> chat, String key) {
        for (int i = chat.size() - 1; i >= 0; i--) {
            if (key.equals(chat.get(i).key())) {
                historyWindow = Math.max(historyWindow, chat.size() - i + HISTORY_PAGE / 2);
                return;
            }
        }
    }

    // ---- ожидающие отправки ----

    /** Ожидающие этого чата; зависшие дольше таймаута (и не упавшие) забываются. */
    List<Pending> visiblePending(long now) {
        pending.removeIf(item -> !isFailed(item) && now - item.sentAt() > PENDING_TIMEOUT_MS);
        return pending.stream().filter(item -> item.partner().equalsIgnoreCase(partner)).toList();
    }

    // собеседник ушёл из сети после отправки — сообщение не доставлено
    boolean isFailed(Pending item) {
        return messages.offlineAt(item.partner()) >= item.sentAt();
    }

    // ---- отправка ----

    // false — не ушло: нет соединения или дополнение не пустило
    boolean sendText(String text, MessageReply reply) {
        if (partner == null || isReadOnly()) {
            return false;
        }
        if (isNotes()) {
            messages.addNote(new PrivateMessage(true, text, clock.getAsLong(), null, List.of(), null, null, null, reply));
        } else {
            // другу по сети — сквозным шифрованием через сервер сети, остальным — /msg
            Optional<String> problem = route.send(partner, text, null, reply, this::warn);
            if (problem.isPresent()) {
                warn(problem.get());
                return false;
            }
            pending.add(new Pending(partner, text, clock.getAsLong(), reply));
        }
        warning = "";
        feedback.sent();
        return true;
    }

    // голосовые — только другу по сети: им нужно сквозное шифрование вложений
    boolean canSendVoice() {
        return partner != null && !isReadOnly() && !isNotes() && !isOfficial() && !isGroup()
                && route.supportsMedia(partner);
    }

    /** true — голосовое ушло в отправку. */
    boolean sendVoice(byte[] data, int centiseconds, MessageReply reply) {
        if (!canSendVoice()) {
            return false;
        }
        Optional<String> problem = route.sendVoice(partner, data, centiseconds, reply, this::warn);
        if (problem.isPresent()) {
            warn(problem.get());
            return false;
        }
        info("Голосовое отправляется…");
        return true;
    }

    // опрос из окна: уходит каждому участнику группы, у себя появится, когда дойдёт
    void sendPoll(String chat, String question, List<String> options) {
        GroupChats groups = groups();
        if (groups == null) {
            warn("Опросы работают через сеть друзей AFriends — она сейчас не подключена");
            return;
        }
        groups.createPoll(chat, question, options, this::warn);
    }

    // ---- подписи ----

    void warn(String text) {
        warning = text;
        warningAt = clock.getAsLong();
        feedback.warned();
    }

    // подпись от дополнения (не ошибка) — тем же местом над полем ввода
    void info(String text) {
        warning = text;
        warningAt = clock.getAsLong();
    }

    /** Подпись, которую сейчас видно; пусто — нечего показывать или она устарела. */
    String visibleWarning(long now) {
        return warning.isEmpty() || now - warningAt > WARNING_MS ? "" : warning;
    }

    // ---- имена ----

    // префикс автора в открытой группе: перед ником над его сообщениями
    String authorPrefix(String author) {
        GroupChats groups = groups();
        if (author == null || partner == null || groups == null || !isGroup()) {
            return "";
        }
        return groups.prefixOf(partner, author);
    }

    // как чат называется для людей: в окне пересылки и в пометке «Переслано от …»
    String chatName(String entry) {
        if (entry == null) {
            return "";
        }
        if (ANNOUNCEMENTS.equals(entry)) {
            return ANNOUNCEMENTS_TITLE;
        }
        if (PrivateMessages.NOTES.equals(entry)) {
            return NOTES_TITLE;
        }
        return messages.displayName(entry);
    }

    // куда можно переслать: Избранное, AFriends и переписки, кроме текущей
    List<String> forwardTargets() {
        List<String> targets = new ArrayList<>();
        targets.add(PrivateMessages.NOTES);
        targets.add(PrivateMessages.OFFICIAL);
        for (String entry : messages.partners()) {
            if (!targets.contains(entry)) {
                targets.add(entry);
            }
        }
        targets.removeIf(entry -> entry.equalsIgnoreCase(partner == null ? "" : partner));
        return targets;
    }
}
