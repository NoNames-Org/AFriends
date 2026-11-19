package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.addon.AddonRegistry;
import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.core.GroupChats;
import dev.no.name.afriends.core.MessageImage;
import dev.no.name.afriends.core.MessageRoute;
import dev.no.name.afriends.core.PrivateMessage;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.animation.Animated;
import dev.no.name.afriends.gui.animation.Easing;
import dev.no.name.afriends.gui.animation.Motion;
import dev.no.name.afriends.gui.media.ChatMedia;
import dev.no.name.afriends.gui.render.Colors;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.icon.Icon;
import dev.no.name.afriends.gui.sound.UiSound;
import dev.no.name.afriends.gui.sound.UiSounds;
import dev.no.name.afriends.net.announcements.AnnouncementFeed;
import dev.no.name.afriends.net.announcements.AnnouncementPoll;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static dev.no.name.afriends.gui.friends.MessengerLayout.CHAT_WIDTH;
import static dev.no.name.afriends.gui.friends.MessengerLayout.CHAT_X;
import static dev.no.name.afriends.gui.friends.MessengerLayout.HEADER_HEIGHT;
import static dev.no.name.afriends.gui.friends.MessengerLayout.HEIGHT;
import static dev.no.name.afriends.gui.friends.MessengerLayout.PAD;
import static dev.no.name.afriends.gui.friends.MessengerLayout.SCROLL_STEP;
import static dev.no.name.afriends.gui.friends.MessengerLayout.WIDTH;

/**
 * Лента открытого чата: закреп сверху, пузыри с разделителями дней, анимация появления, прокрутка с догрузкой
 * истории, переход к сообщению с подсветкой и клики по пузырям, карточкам и полосам вокруг ленты
 */
final class MessageFeedView {
    // за сколько пикселей до верха подгружать следующую порцию
    private static final int HISTORY_PRELOAD = 200;
    // подсветка сообщения, к которому перешли по ответу или закрепу
    private static final long HIGHLIGHT_MS = 1400;

    private final ChatController controller;
    private final BubbleContext bubbleContext;
    private final ChatMedia chatMedia;
    private final AddonRegistry addons;
    private final AnnouncementFeed announcements;
    private final MessageRoute route;
    private final Selection selection;
    private final ComposerView composer;
    private final MessengerPopups popups;
    private final Executor gameThread;
    private final PinnedBar pinnedBar = new PinnedBar();
    // кнопки карточек дополнений, нарисованные в этом кадре
    private final List<MessageBubbles.CardButton> cardButtons = new ArrayList<>();
    // где нарисованы пузыри на этом кадре: клик по картинке, реакциям, правый клик по сообщению
    private final List<MessageBubbles.Drawn> drawn = new ArrayList<>();
    private final Animated scrollView = new Animated(0, Motion.FAST);
    private int scroll;
    private String highlightKey;
    private long highlightAt;

    MessageFeedView(ChatController controller, BubbleContext bubbleContext, ChatMedia chatMedia, AddonRegistry addons,
                    AnnouncementFeed announcements, MessageRoute route, Selection selection, ComposerView composer,
                    MessengerPopups popups, Executor gameThread) {
        this.controller = controller;
        this.bubbleContext = bubbleContext;
        this.chatMedia = chatMedia;
        this.addons = addons;
        this.announcements = announcements;
        this.route = route;
        this.selection = selection;
        this.composer = composer;
        this.popups = popups;
        this.gameThread = gameThread;
    }

    /** Другой чат: лента с низа, без подсветки, закреп забывает прежний. */
    void reset() {
        pinnedBar.reset();
        highlightKey = null;
        scroll = 0;
        scrollView.snap(0);
    }

    void scroll(int step) {
        scroll = Math.max(0, scroll + step);
        scrollView.setTarget(scroll);
    }

    void scrollToBottom() {
        scroll = 0;
        scrollView.setTarget(0);
    }

    // верх ленты сообщений: под шапкой, а если есть закреп — под его плашкой
    private int top() {
        return HEADER_HEIGHT + 4 + (controller.pinnedMessages().isEmpty() ? 0 : PinnedBar.HEIGHT);
    }

    private int bottom() {
        return composer.top() - 6;
    }

    // ---- отрисовка ----

    /** Пока чат не выбран — подсказка посередине. */
    static void drawNoChat(Gfx g) {
        float centerX = CHAT_X + CHAT_WIDTH / 2f;
        float drift = (Easing.pulse(Motion.now(), Theme.IDLE_PERIOD) - 0.5f) * 3;
        Draw.icon(g, Icon.MESSAGE, centerX - 10, HEIGHT / 2f - 24 + drift, 20, Theme.TEXT_FAINT);
        Draw.textCentered(g, "Выбери чат слева", centerX, HEIGHT / 2f + 2, Theme.TEXT_DIM);
    }

    // плашка закрепа рисуется и при пустом списке: так она забывает открепленное и не ловит по нему клики
    void drawPinnedBar(Gfx g, int hoverX, int mouseY) {
        pinnedBar.draw(g, controller.pinnedMessages(), CHAT_X + 1, HEADER_HEIGHT + 1, CHAT_WIDTH - 2, hoverX, mouseY);
    }

    void drawMessages(Gfx g) {
        int top = top();
        int bottom = bottom();
        List<PrivateMessage> chat = controller.chatMessages(controller.partner());
        if (controller.trackNewMessages(chat) && scroll < SCROLL_STEP) {
            scrollToBottom();
        }
        long now = System.currentTimeMillis();
        List<ChatController.Pending> mine = controller.visiblePending(now);
        // переход к сообщению старше загруженной части: сначала догружаем её, прокрутка — когда сообщение в ленте
        if (controller.jumpKey() != null) {
            controller.revealForJump(chat, controller.jumpKey());
        }
        int from = controller.historyFrom(chat.size());
        List<PrivateMessage> loaded = chat.subList(from, chat.size());
        List<PrivateMessage> shown = new ArrayList<>(loaded);
        mine.forEach(item -> shown.add(new PrivateMessage(true, item.text(), item.sentAt(), null, List.of(), null, null, null,
                item.reply())));
        if (shown.isEmpty()) {
            String text = controller.isNotes() ? "Пиши сюда заметки — их видишь только ты" : "Сообщений пока нет — напиши первым";
            Draw.textCentered(g, text, CHAT_X + CHAT_WIDTH / 2f, (top + bottom) / 2f, Theme.TEXT_SOFT);
            return;
        }
        // при выделении слева кружки: входящие сдвигаются вправо
        float left = CHAT_X + PAD + (selection.isActive() ? Selection.GUTTER : 0);
        float right = WIDTH - PAD;
        List<FeedRows.Row> rows = FeedRows.build(bubbleContext, shown, right - left, loaded.size(), controller.partner());
        cardButtons.clear();
        drawn.clear();
        float content = FeedRows.height(rows);
        int maxScroll = (int) Math.max(0, content - (bottom - top));
        if (scroll > maxScroll) {
            scroll = maxScroll;
            scrollView.setTarget(scroll);
        }
        if (controller.jumpKey() != null) {
            jump(rows, content, top, bottom, maxScroll);
        }
        // докрутили почти до верха — следующая порция истории. Лента прижата к низу, поэтому старые сообщения
        // добавляются сверху и видимое место не прыгает
        if (from > 0 && scroll >= maxScroll - HISTORY_PRELOAD) {
            controller.loadOlder();
        }
        g.enableScissor(CHAT_X + 1, top, WIDTH - 1, bottom);
        float y = bottom - content + Math.round(scrollView.get());
        for (FeedRows.Row row : rows) {
            if (y + row.height() >= top && y <= bottom) {
                drawRow(g, row, y, top, bottom, left, right, from, loaded.size(), mine, now);
            }
            y += row.height();
        }
        g.disableScissor();
    }

    private void drawRow(Gfx g, FeedRows.Row row, float y, int top, int bottom, float left, float right, int from,
                         int loadedCount, List<ChatController.Pending> mine, long now) {
        if (row.day() != null) {
            FeedRows.drawDay(g, row.day(), y);
            return;
        }
        boolean isPending = row.index() >= loadedCount;
        ChatController.Pending item = isPending ? mine.get(row.index() - loadedCount) : null;
        long start = isPending ? item.sentAt() : controller.appearStart(from + row.index());
        MessageBubbles.Stamp stamp = !isPending ? MessageBubbles.Stamp.TIME
                : controller.isFailed(item) ? MessageBubbles.Stamp.FAILED : MessageBubbles.Stamp.PENDING;
        MessageBubbles.Drawn bubble = FeedRows.drawAnimated(g, row.bubble(), left, right, y, start, stamp, now, bubbleContext);
        if (bubble.card() != null && y >= top && y + row.height() <= bottom) {
            cardButtons.add(bubble.card());
        }
        if (!isPending) {
            drawn.add(bubble);
            drawMarks(g, bubble, now);
        }
    }

    // подсветка после перехода и кружок выделения слева
    private void drawMarks(Gfx g, MessageBubbles.Drawn bubble, long now) {
        String key = bubble.message().key();
        if (key.equals(highlightKey) && now - highlightAt < HIGHLIGHT_MS) {
            float fade = 1 - (now - highlightAt) / (float) HIGHLIGHT_MS;
            Draw.roundedRect(g, CHAT_X + 1, bubble.y() - 1, CHAT_WIDTH - 2, bubble.height() + 2, 0,
                    Colors.fade(Theme.ACCENT, 0.18f * fade));
        }
        if (selection.isActive()) {
            if (selection.contains(bubble.message())) {
                Draw.rect(g, CHAT_X + 1, bubble.y() - 1, CHAT_WIDTH - 2, bubble.height() + 2, Colors.fade(Theme.ACCENT, 0.1f));
            }
            selection.drawMark(g, bubble.message(), CHAT_X + PAD, bubble.y() + bubble.height() / 2f);
        }
    }

    // прокрутить так, чтобы сообщение из перехода оказалось посередине, и подсветить его
    private void jump(List<FeedRows.Row> rows, float content, int top, int bottom, int maxScroll) {
        String key = controller.jumpKey();
        controller.clearJump();
        float offset = 0;
        for (FeedRows.Row row : rows) {
            if (row.bubble() != null && key.equals(row.bubble().message().key())) {
                float rowCenter = offset + row.height() / 2f;
                scroll = Math.round(Math.max(0, Math.min(maxScroll, content - rowCenter - (bottom - top) / 2f)));
                scrollView.setTarget(scroll);
                highlightKey = key;
                highlightAt = System.currentTimeMillis();
                return;
            }
            offset += row.height();
        }
        controller.info("Сообщения уже нет: оно удалено");
    }

    // ---- клики ----

    // полосы вокруг ленты: выделение в шапке, плашка закрепа, ответ над полем ввода. true — клик по одной из них
    boolean clickBars(double x, double y) {
        String partner = controller.partner();
        if (partner == null) {
            return false;
        }
        if (selection.isActive() && y < HEADER_HEIGHT && x >= CHAT_X) {
            return clickSelectionBar(x, y, partner);
        }
        switch (pinnedBar.click(x, y)) {
            case PinnedBar.Jump jump -> {
                controller.requestJump(jump.message().key());
                return true;
            }
            case PinnedBar.Unpin unpin -> {
                route.pin(partner, unpin.message(), false, route.canSync(partner, List.of(unpin.message())), controller::warn);
                return true;
            }
            case PinnedBar.None none -> { }
        }
        ReplyStrip replyStrip = composer.replyStrip();
        return switch (replyStrip.click(x, y)) {
            case JUMP -> {
                controller.requestJump(replyStrip.reply().id());
                yield true;
            }
            case CANCEL -> true;
            case NONE -> false;
        };
    }

    private boolean clickSelectionBar(double x, double y, String partner) {
        switch (selection.click(x, y)) {
            case FORWARD -> popups.forwardPicker().open(selection.messages(controller.chatMessages(partner)),
                    controller.forwardTargets(), WIDTH, HEIGHT);
            case DELETE -> popups.actions().delete(selection.messages(controller.chatMessages(partner)));
            case CANCEL -> selection.clear();
            case NONE -> {
                return false;
            }
        }
        return true;
    }

    // картинка — открыть крупно; плашка реакции — поставить или снять свою; правый клик — всплывашка реакций
    boolean clickBubble(double x, double y, boolean right) {
        if (y < top() || y > bottom()) {
            return false;
        }
        for (MessageBubbles.Drawn bubble : drawn) {
            // при выделении кликабельна вся строка, а не только пузырь
            boolean onRow = selection.isActive() && y >= bubble.y() && y < bubble.y() + bubble.height() && x >= CHAT_X;
            if (bubble.contains(x, y) || onRow) {
                return clickOn(bubble, x, y, right);
            }
        }
        return false;
    }

    private boolean clickOn(MessageBubbles.Drawn bubble, double x, double y, boolean right) {
        String partner = controller.partner();
        PrivateMessage message = bubble.message();
        boolean canReact = canReact(message);
        MessageImage image = bubble.imageAt(x, y);
        MediaActions media = popups.media();
        if (right) {
            // правый клик по любому сообщению: хотя бы «Переслать» есть всегда
            popups.messageMenu().open(message, popups.actions().items(message, image),
                    canReact ? reaction -> media.react(partner, message, reaction) : null, (float) x, (float) y, WIDTH, HEIGHT);
            UiSounds.play(UiSound.DROPDOWN_OPEN);
            return true;
        }
        if (selection.isActive()) {
            selection.toggle(message);
            return clicked();
        }
        if (bubble.voice() != null && bubble.voice().contains(x, y)) {
            chatMedia.toggleVoice(bubble.voice().voice());
            return clicked();
        }
        PollBlock.Hit option = bubble.pollAt(x, y);
        if (option != null) {
            vote(option);
            return true;
        }
        if (bubble.quoteAt(x, y)) {
            controller.requestJump(message.reply().id());
            return clicked();
        }
        for (ReactionChips.Hit chip : bubble.chips()) {
            if (chip.contains(x, y) && canReact) {
                media.react(partner, message, chip.reaction().equals(message.reaction()) ? null : chip.reaction());
                return clicked();
            }
        }
        if (image != null) {
            popups.viewer().open(image);
            return clicked();
        }
        return false;
    }

    private static boolean clicked() {
        UiSounds.play(UiSound.CLICK);
        return true;
    }

    // реакции — только на сообщения с id и только с другом по сети
    private boolean canReact(PrivateMessage message) {
        String partner = controller.partner();
        return message.id() != null && partner != null && !controller.isReadOnly() && !controller.isNotes()
                && !controller.isOfficial() && popups.media().available(partner);
    }

    // клик по варианту: другой — голос за него, свой — отозвать. Без сети голосовать нельзя
    private void vote(PollBlock.Hit option) {
        int choice = option.poll().mine() == option.option() ? -1 : option.option();
        UiSounds.play(UiSound.CLICK);
        if (option.message() != null) {
            GroupChats groups = controller.groups();
            if (groups == null) {
                controller.warn("Голос не ушёл: нужна сеть друзей AFriends");
                return;
            }
            groups.vote(controller.partner(), option.message(), choice, controller::warn);
            return;
        }
        if (!(option.poll() instanceof AnnouncementPoll poll)) {
            return;
        }
        announcements.votes().vote(poll, choice, fresh -> gameThread.execute(() -> {
            if (fresh == null) {
                controller.warn("Голос не ушёл: нужна сеть друзей AFriends");
                return;
            }
            announcements.replacePoll(fresh);
        }));
    }

    // кнопка карточки дополнения в пузыре
    boolean clickCard(double x, double y) {
        for (MessageBubbles.CardButton button : cardButtons) {
            if (button.contains(x, y)) {
                addons.run("messageCard", button.card().action());
                UiSounds.play(UiSound.SELECT);
                return true;
            }
        }
        return false;
    }
}
