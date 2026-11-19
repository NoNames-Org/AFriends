package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.addon.AddonRegistry;
import dev.no.name.afriends.api.MessageCard;
import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.core.MessageContent;
import dev.no.name.afriends.core.MessageFormat;
import dev.no.name.afriends.core.MessageRoute;
import dev.no.name.afriends.core.NotifySettings;
import dev.no.name.afriends.core.PrivateMessage;
import dev.no.name.afriends.core.PrivateMessages;
import dev.no.name.afriends.core.ProfileRoute;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.animation.Animated;
import dev.no.name.afriends.gui.animation.Motion;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.icon.Icon;
import dev.no.name.afriends.gui.render.text.TextStyle;
import dev.no.name.afriends.gui.sound.UiSound;
import dev.no.name.afriends.gui.sound.UiSounds;
import dev.no.name.afriends.gui.widget.TextFieldWidget;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static dev.no.name.afriends.gui.friends.MessengerLayout.CHAT_X;
import static dev.no.name.afriends.gui.friends.MessengerLayout.FIELD_HEIGHT;
import static dev.no.name.afriends.gui.friends.MessengerLayout.HEIGHT;
import static dev.no.name.afriends.gui.friends.MessengerLayout.LIST_WIDTH;
import static dev.no.name.afriends.gui.friends.MessengerLayout.LIST_X;
import static dev.no.name.afriends.gui.friends.MessengerLayout.PAD;
import static dev.no.name.afriends.gui.friends.MessengerLayout.ROW_FACE;
import static dev.no.name.afriends.gui.friends.MessengerLayout.ROW_HEIGHT;
import static dev.no.name.afriends.gui.friends.MessengerLayout.inside;
import static dev.no.name.afriends.gui.friends.MessengerLayout.listTop;

/** Список чатов слева: поиск, архив, служебные строки, превью последнего сообщения, черновики и значки. */
final class ChatListView {
    private static final int ARCHIVE_COLOR = 0xFF6B7280;
    private static final Pattern NICK = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yy");

    private final ChatController controller;
    private final PrivateMessages messages;
    private final NotifySettings alerts;
    private final AddonRegistry addons;
    private final MessageRoute route;
    private final ChatTiles tiles;
    private final ChatNavigator navigator;
    private final ChatMenus menus;
    private final TextFieldWidget search = new TextFieldWidget(Icon.SEARCH, "Поиск").withMaxLength(16);
    private final Animated scrollView = new Animated(0, Motion.FAST);
    private int scroll;

    ChatListView(ChatController controller, PrivateMessages messages, NotifySettings alerts, AddonRegistry addons,
                 MessageRoute route, ChatTiles tiles, ChatNavigator navigator, ChatMenus menus) {
        this.controller = controller;
        this.messages = messages;
        this.alerts = alerts;
        this.addons = addons;
        this.route = route;
        this.tiles = tiles;
        this.navigator = navigator;
        this.menus = menus;
        search.setBounds(LIST_X + PAD, PAD, LIST_WIDTH - PAD * 2, FIELD_HEIGHT);
    }

    TextFieldWidget search() {
        return search;
    }

    // ---- строки ----

    List<ListEntry> entries() {
        String typed = search.getText().strip();
        String query = typed.toLowerCase(Locale.ROOT);
        List<ListEntry> result = new ArrayList<>();
        if (controller.archiveView()) {
            result.add(ListEntry.ARCHIVE_BACK);
            addMatching(result, messages.archivedPartners(), query);
            return result;
        }
        if (query.isEmpty() && !messages.archivedPartners().isEmpty()) {
            result.add(ListEntry.ARCHIVE);
        }
        if (query.isEmpty() || "объявления".contains(query)) {
            result.add(ListEntry.ANNOUNCEMENTS);
        }
        if (query.isEmpty() || "избранное".contains(query)) {
            result.add(new ListEntry.Chat(PrivateMessages.NOTES));
        }
        addMatching(result, messages.partners(), query);
        if (!query.isEmpty()) {
            // поиск находит и чаты из архива
            addMatching(result, messages.archivedPartners(), query);
            // ник, с которым ещё нет переписки, — сразу «Написать»
            boolean known = result.stream().anyMatch(entry -> messages.displayName(entry.chat()).equalsIgnoreCase(typed));
            if (!known && NICK.matcher(typed).matches()) {
                result.add(new ListEntry.WriteTo(typed));
            }
        }
        return result;
    }

    private void addMatching(List<ListEntry> result, List<String> chats, String query) {
        for (String name : chats) {
            if (query.isEmpty() || messages.displayName(name).toLowerCase(Locale.ROOT).contains(query)) {
                result.add(new ListEntry.Chat(name));
            }
        }
    }

    ListEntry entryAt(double x, double y) {
        if (x < LIST_X || x >= CHAT_X || y < listTop() || y >= HEIGHT - 4) {
            return null;
        }
        List<ListEntry> entries = entries();
        int index = (int) Math.floor((y - listTop() + Math.round(scrollView.get())) / ROW_HEIGHT);
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    // ---- отрисовка ----

    void draw(Gfx g, int mouseX, int mouseY) {
        List<ListEntry> entries = entries();
        int top = listTop();
        int bottom = HEIGHT - 4;
        int maxScroll = Math.max(0, entries.size() * ROW_HEIGHT - (bottom - top));
        if (scroll > maxScroll) {
            scroll = maxScroll;
            scrollView.setTarget(scroll);
        }
        int shownScroll = Math.round(scrollView.get());
        g.enableScissor(LIST_X + 1, top, CHAT_X, bottom);
        for (int i = 0; i < entries.size(); i++) {
            int rowY = top + i * ROW_HEIGHT - shownScroll;
            if (rowY + ROW_HEIGHT < top || rowY > bottom) {
                continue;
            }
            ListEntry entry = entries.get(i);
            if (entry.isServiceRow()) {
                drawServiceRow(g, entry, rowY, mouseX, mouseY);
            } else {
                drawChat(g, entry.chat(), rowY, mouseX, mouseY);
            }
        }
        g.disableScissor();
    }

    // служебная строка: значок и подпись вместо чата
    private void drawServiceRow(Gfx g, ListEntry entry, int rowY, int mouseX, int mouseY) {
        int x = LIST_X + 4;
        int width = LIST_WIDTH - 8;
        if (inside(mouseX, mouseY, x, rowY, width, ROW_HEIGHT)) {
            Draw.roundedRect(g, x, rowY, width, ROW_HEIGHT, Theme.RADIUS, Theme.CARD_HOVER);
        }
        int faceX = x + 6;
        int faceY = rowY + (ROW_HEIGHT - ROW_FACE) / 2;
        float textX = faceX + ROW_FACE + 7;
        int textWidth = (int) (x + width - 6 - textX);
        switch (entry) {
            case ListEntry.ArchiveBack back -> {
                ChatTiles.round(g, Icon.ARROW_LEFT, ARCHIVE_COLOR, faceX, faceY, ROW_FACE);
                Draw.text(g, "Архив", textX, rowY + 6, Theme.TEXT, TextStyle.BODY_MEDIUM);
                Draw.text(g, Draw.trim("Назад ко всем чатам", textWidth), textX, rowY + 17, Theme.TEXT_SOFT, TextStyle.SMALL);
            }
            case ListEntry.ArchiveRow archive -> drawArchiveRow(g, x, width, rowY, faceX, faceY, textX, textWidth);
            case ListEntry.WriteTo write -> {
                ChatTiles.face(g, tiles.skinOf(write.name()), faceX, faceY, ROW_FACE);
                Draw.text(g, Draw.trim(write.name(), textWidth), textX, rowY + 6, Theme.TEXT, TextStyle.BODY_MEDIUM);
                Draw.text(g, Draw.trim("Написать сообщение", textWidth), textX, rowY + 17, Theme.ACCENT, TextStyle.SMALL);
            }
            default -> { }
        }
    }

    private void drawArchiveRow(Gfx g, int x, int width, int rowY, int faceX, int faceY, float textX, int textWidth) {
        ChatTiles.round(g, Icon.ARCHIVE, ARCHIVE_COLOR, faceX, faceY, ROW_FACE);
        int count = messages.archivedPartners().size();
        int unreadCount = messages.archivedUnread();
        Draw.text(g, "Архив", textX, rowY + 6, Theme.TEXT, TextStyle.BODY_MEDIUM);
        Draw.text(g, Draw.trim("Чатов: " + count, textWidth), textX, rowY + 17, Theme.TEXT_SOFT, TextStyle.SMALL);
        if (unreadCount > 0) {
            String badge = String.valueOf(unreadCount);
            float badgeWidth = Draw.width(badge, TextStyle.SMALL) + 8;
            float badgeX = x + width - 6 - badgeWidth;
            Draw.roundedRect(g, badgeX, rowY + 16, badgeWidth, 10, 5, Theme.CARD_HOVER);
            Draw.text(g, badge, badgeX + 4, rowY + 18, Theme.TEXT_SOFT, TextStyle.SMALL);
        }
    }

    private void drawChat(Gfx g, String entry, int rowY, int mouseX, int mouseY) {
        int x = LIST_X + 4;
        int width = LIST_WIDTH - 8;
        boolean selected = controller.isSelected(entry);
        if (selected) {
            Draw.roundedRect(g, x, rowY, width, ROW_HEIGHT, Theme.RADIUS, Theme.CARD_SELECTED);
            Draw.roundedRect(g, x, rowY + 6, 2, ROW_HEIGHT - 12, 1, Theme.ACCENT);
        } else if (inside(mouseX, mouseY, x, rowY, width, ROW_HEIGHT)) {
            Draw.roundedRect(g, x, rowY, width, ROW_HEIGHT, Theme.RADIUS, Theme.CARD_HOVER);
        }
        boolean announcements = ChatController.ANNOUNCEMENTS.equals(entry);
        boolean notes = announcements || PrivateMessages.NOTES.equals(entry);
        int faceX = x + 6;
        tiles.entry(g, entry, faceX, rowY + (ROW_HEIGHT - ROW_FACE) / 2, ROW_FACE);
        float textX = faceX + ROW_FACE + 7;
        PrivateMessage last = controller.lastMessage(entry);
        drawTitleLine(g, entry, last, announcements, notes, x, width, rowY, textX);
        drawPreviewLine(g, entry, last, announcements, notes, selected, x, width, rowY, textX);
    }

    // ник или название, справа дата и значки «без звука» и «закреплён»
    private void drawTitleLine(Gfx g, String entry, PrivateMessage last, boolean announcements, boolean notes,
                               int x, int width, int rowY, float textX) {
        String date = last == null ? "" : formatDate(last.time());
        float dateWidth = Draw.width(date, TextStyle.SMALL);
        boolean pinned = !notes && messages.isPinned(entry);
        boolean muted = !notes && alerts.isMuted(entry);
        float pinSpace = (pinned ? Theme.ICON_SMALL + 3 : 0) + (muted ? Theme.ICON_SMALL + 3 : 0);
        int nameWidth = (int) (x + width - 6 - textX - dateWidth - pinSpace - 4);
        String title = announcements ? ChatController.ANNOUNCEMENTS_TITLE : notes ? ChatController.NOTES_TITLE
                : messages.displayName(entry);
        Draw.text(g, Draw.trim(title, nameWidth), textX, rowY + 6, Theme.TEXT, TextStyle.BODY_MEDIUM);
        Draw.text(g, date, x + width - 6 - dateWidth, rowY + 7, Theme.TEXT_DIM, TextStyle.SMALL);
        float markX = x + width - 6 - dateWidth - pinSpace;
        if (muted) {
            Draw.icon(g, Icon.BELL_OFF, markX, rowY + 6, Theme.ICON_SMALL - 1, Theme.TEXT_DIM);
            markX += Theme.ICON_SMALL + 3;
        }
        if (pinned) {
            Draw.icon(g, Icon.PIN, markX, rowY + 6, Theme.ICON_SMALL - 1, Theme.TEXT_DIM);
        }
    }

    // превью последнего сообщения или черновик, справа число непрочитанных
    private void drawPreviewLine(Gfx g, String entry, PrivateMessage last, boolean announcements, boolean notes,
                                 boolean selected, int x, int width, int rowY, float textX) {
        int unread = notes ? 0 : messages.unread(entry);
        float badgeWidth = unread > 0 ? Draw.width(String.valueOf(unread), TextStyle.SMALL) + 8 : 0;
        String draft = selected || announcements ? "" : messages.draft(entry).replaceAll("\\s+", " ").strip();
        String preview = last == null ? (notes ? "Твой личный чат для заметок" : "Пусто")
                : (last.fromMe() && !notes ? "Ты: " : "") + previewText(entry, last);
        int previewWidth = (int) (x + width - 6 - textX - badgeWidth - (badgeWidth > 0 ? 4 : 0));
        if (!draft.isEmpty()) {
            String label = "Черновик: ";
            float labelWidth = Draw.width(label, TextStyle.SMALL);
            Draw.text(g, label, textX, rowY + 17, Theme.DANGER, TextStyle.SMALL);
            Draw.text(g, Draw.trim(draft, (int) (previewWidth - labelWidth)), textX + labelWidth, rowY + 17, Theme.TEXT_SOFT, TextStyle.SMALL);
        } else {
            Draw.text(g, Draw.trim(preview, previewWidth), textX, rowY + 17, Theme.TEXT_SOFT, TextStyle.SMALL);
        }
        if (unread > 0) {
            float badgeX = x + width - 6 - badgeWidth;
            Draw.roundedRect(g, badgeX, rowY + 16, badgeWidth, 10, 5, Theme.ACCENT);
            Draw.text(g, String.valueOf(unread), badgeX + 4, rowY + 18, Theme.ACCENT_TEXT, TextStyle.SMALL);
        }
    }

    // текст в списке чатов: скрыт для стримера, у карточки дополнения — её заголовок
    private String previewText(String entry, PrivateMessage last) {
        if (addons.hideMessages()) {
            return "Скрыто";
        }
        if (!last.images().isEmpty()) {
            return MessageContent.preview(last);
        }
        MessageCard.Card card = addons.card(entry, last.text(), last.fromMe());
        // многострочное сообщение в превью — одной строкой: переносы и лишние пробелы в один пробел
        String text = card == null ? MessageFormat.plain(last.text()).replaceAll("\\s+", " ").strip() : card.title();
        return last.author() != null && !last.fromMe() ? last.author() + ": " + text : text;
    }

    private static String formatDate(long time) {
        LocalDate date = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate();
        if (date.equals(LocalDate.now())) {
            return TIME.format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()));
        }
        return DATE.format(date);
    }

    // ---- ввод ----

    /** Клик по строке списка; false — мимо списка. */
    boolean click(double x, double y, boolean right) {
        ListEntry entry = entryAt(x, y);
        if (entry == null) {
            return false;
        }
        if (entry.isServiceRow()) {
            if (!right) {
                clickServiceRow(entry);
            }
            return true;
        }
        String chat = entry.chat();
        if (right && !(entry instanceof ListEntry.Announcements)) {
            menus.openChatMenu(chat, x, y);
            UiSounds.play(UiSound.DROPDOWN_OPEN);
        } else if (!right && !controller.isSelected(chat)) {
            navigator.select(chat);
            if (!controller.isReadOnly()) {
                navigator.focusInput();
            } else {
                navigator.blurInput();
            }
            UiSounds.play(UiSound.CLICK);
        }
        return true;
    }

    // клик по служебной строке
    private void clickServiceRow(ListEntry entry) {
        UiSounds.play(UiSound.CLICK);
        scroll = 0;
        scrollView.snap(0);
        if (entry instanceof ListEntry.WriteTo write) {
            search.setText("");
            startChat(write.name());
            return;
        }
        controller.setArchiveView(entry instanceof ListEntry.ArchiveRow);
    }

    // переписка с игроком по нику из поиска: игрок есть в сети AFriends — чат по сети со сквозным шифрованием,
    // нет — как раньше, /msg этого сервера
    private void startChat(String name) {
        controller.setArchiveView(false);
        ProfileRoute profile = route.profile();
        Runnable open = () -> {
            messages.ensureChat(name);
            navigator.select(name);
            navigator.focusInput();
        };
        if (profile == null) {
            open.run();
            return;
        }
        controller.info("Ищу " + name + " в сети AFriends…");
        profile.startChat(name, () -> {
            open.run();
            controller.info("Чат с " + name + " — через сеть AFriends, сквозное шифрование");
        }, () -> {
            open.run();
            controller.info(name + " нет в сети AFriends — сообщение уйдёт через /msg этого сервера");
        }, controller::warn);
    }

    /** Колесо над списком. */
    void scroll(int step) {
        scroll = Math.max(0, scroll - step);
        scrollView.setTarget(scroll);
    }

    /** Набрали текст в поиске — список с начала. */
    void searchChanged() {
        scroll = 0;
        scrollView.setTarget(0);
    }
}
