package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.addon.AddonRegistry;
import dev.no.name.afriends.gui.media.ChatMedia;
import dev.no.name.afriends.gui.UiServices;
import dev.no.name.afriends.core.MessageContent;
import dev.no.name.afriends.core.MessageFormat;
import dev.no.name.afriends.core.voice.VoiceClip;
import dev.no.name.afriends.core.GroupChats;
import dev.no.name.afriends.core.GroupPermissions;
import dev.no.name.afriends.net.announcements.AnnouncementPoll;
import dev.no.name.afriends.core.NotifySettings;
import dev.no.name.afriends.gui.widget.ConfirmBox;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import dev.no.name.afriends.net.announcements.AnnouncementFeed;
import dev.no.name.afriends.api.ComposeAction;
import dev.no.name.afriends.api.FriendAction;
import dev.no.name.afriends.api.MessageCard;
import dev.no.name.afriends.compat.Compat;
import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.compat.ScreenBase;
import dev.no.name.afriends.core.Friend;
import dev.no.name.afriends.core.FriendManager;
import dev.no.name.afriends.core.IgnoreList;
import dev.no.name.afriends.core.MessageImage;
import dev.no.name.afriends.core.MessageReply;
import dev.no.name.afriends.core.MessageRoute;
import dev.no.name.afriends.core.PrivateMessage;
import dev.no.name.afriends.core.PrivateMessages;
import dev.no.name.afriends.core.ProfileRoute;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.animation.Animated;
import dev.no.name.afriends.gui.animation.Easing;
import dev.no.name.afriends.gui.animation.Motion;
import dev.no.name.afriends.gui.render.Colors;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.blur.MenuBlur;
import dev.no.name.afriends.gui.render.icon.Icon;
import dev.no.name.afriends.gui.render.text.TextStyle;
import dev.no.name.afriends.gui.sound.UiSound;
import dev.no.name.afriends.gui.sound.UiSounds;
import dev.no.name.afriends.gui.widget.ButtonWidget;
import dev.no.name.afriends.gui.widget.IconButtonWidget;
import dev.no.name.afriends.gui.widget.ContainerWidget;
import dev.no.name.afriends.gui.widget.TextFieldWidget;
import dev.no.name.afriends.gui.widget.Widget;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Matrix3x2fStack;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class MessengerScreen extends ScreenBase {
    private static final int WIDTH = 470;
    private static final int HEIGHT = 290;
    private static final int SCREEN_MARGIN = 20;
    private static final int RAIL_WIDTH = 32;
    private static final int LIST_X = RAIL_WIDTH;
    private static final int LIST_WIDTH = 150;
    private static final int CHAT_X = LIST_X + LIST_WIDTH;
    private static final int CHAT_WIDTH = WIDTH - CHAT_X;
    private static final int PAD = 8;
    private static final int RAIL_BUTTON = 22;
    private static final int RAIL_STEP = 27;
    private static final int FIELD_HEIGHT = 18;
    private static final int ROW_HEIGHT = 30;
    private static final int ROW_FACE = 20;
    private static final int HEADER_HEIGHT = 40;
    private static final int HEADER_FACE = 24;
    private static final int INPUT_HEIGHT = 20;
    // сколько строк поле ввода показывает, прежде чем перестать расти вверх
    private static final int MAX_INPUT_LINES = 6;
    private static final int SEND_WIDTH = 26;
    // полоса ввода как в Telegram: скрепка слева, поле, значки справа; «отправить» — рядом
    private static final int BAR_X = CHAT_X + PAD;
    private static final int BAR_WIDTH = CHAT_WIDTH - PAD * 2 - SEND_WIDTH - 4;
    private static final int BAR_ICON = 16;
    private static final int SEPARATOR_HEIGHT = 18;
    private static final int SCROLL_STEP = 16;
    private static final int MAX_TEXT = 200;
    // сколько последних сообщений чата отмечаются прочитанными при открытии
    private static final int READ_WINDOW = 50;
    // лента раскладывает не всю историю, а последние сообщения: столько сразу и столько ещё при прокрутке к верху
    private static final int HISTORY_PAGE = 80;
    // за сколько пикселей до верха подгружать следующую порцию
    private static final int HISTORY_PRELOAD = 200;
    private static final int DANGER = 0xFFFF6B6B;
    private static final int NOTES_COLOR = 0xFF3D7BFF;
    private static final int ANNOUNCEMENTS_COLOR = 0xFFE0313F;
    /** Чат AFriends: переписка с админами мода через портал. Не игнорируется и не закрепляется. */
    private static final int OFFICIAL_COLOR = 0xFFB3122A;
    /** Чат «Объявления»: всегда первый, только для чтения, не очищается и не удаляется. Пишут с portal.no-names.com. */
    private static final String ANNOUNCEMENTS = "$announcements";
    private static final String ANNOUNCEMENTS_TITLE = "Объявления";
    private static final long ANNOUNCEMENTS_SINCE = java.time.LocalDate.of(2026, 11, 16)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    /** Пока с сервера ничего не пришло. */
    private static final List<PrivateMessage> ANNOUNCEMENTS_EMPTY = List.of(new PrivateMessage(false,
            "Здесь будут новости от AFriends: обновления мода и важные изменения. Писать сюда нельзя.",
            ANNOUNCEMENTS_SINCE));
    private static final long WARNING_MS = 4000;
    // подсветка сообщения, к которому перешли по ответу или закрепу
    private static final long HIGHLIGHT_MS = 1400;

    private static final long APPEAR_MS = 260;
    private static final long PENDING_TIMEOUT_MS = 6000;
    private static final float PENDING_ALPHA = 0.6f;
    private static final float SLIDE_Y = 10;
    private static final float SLIDE_X = 8;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yy");
    private static final String[] MONTHS = {
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря"
    };

    public record Context(FriendManager friends, PrivateMessages messages, IgnoreList ignored,
                          MessageRoute route, Runnable save, NotifySettings alerts, UiServices ui) {
    }

    private record Pending(String partner, String text, long sentAt, MessageReply reply) {
    }

    private record Row(String day, MessageBubbles.Bubble bubble, int index, float height) {
    }

    private enum RailAction { READ_ALL, ALL_CHATS, NOTES, NEW_GROUP }
    private static final int ARCHIVE_COLOR = 0xFF6B7280;
    // служебные строки списка чатов: «Архив», «← Назад» из архива и «Написать <ник>» из поиска
    private static final String ARCHIVE_ROW = "$row:archive";
    private static final String ARCHIVE_BACK = "$row:back";
    private static final String WRITE_PREFIX = "$row:write:";
    private static final java.util.regex.Pattern NICK = java.util.regex.Pattern.compile("[A-Za-z0-9_]{3,16}");

    private final Context context;
    private final PrivateMessages messages;
    private final UiServices ui;
    private final AddonRegistry addons;
    private final AnnouncementFeed announcements;
    private final ChatMedia chatMedia;
    private final BubbleContext bubbleContext;
    private final List<Pending> pending = new ArrayList<>();

    private final Map<Integer, Long> appearAt = new HashMap<>();
    private final ContainerWidget root = new ContainerWidget();
    private final ContainerWidget composer = new ContainerWidget();
    private final TextFieldWidget search = new TextFieldWidget(Icon.SEARCH, "Поиск").withMaxLength(16);
    private final TextFieldWidget input;
    // многострочное поле растёт вверх: храним его столбец и насколько оно сейчас выше одной строки
    private int inputX;
    private int inputWidth;
    private int inputExtra;
    private final ChatMenu menu = new ChatMenu();
    private final GroupEditor groupEditor = new GroupEditor(new GroupHost());
    private final PollComposer pollComposer = new PollComposer(this::sendPoll);
    private final GroupCreator groupCreator;
    private final ProfilePanel profilePanel;
    private final MessengerSettingsPanel settingsPanel;
    private final VoiceStrip voiceStrip = new VoiceStrip(this::sendVoice, this::warn);
    // кнопки карточек дополнений, нарисованные в этом кадре
    private final List<MessageBubbles.CardButton> cardButtons = new ArrayList<>();
    // где нарисованы пузыри на этом кадре: клик по картинке, реакциям, правый клик по сообщению
    private final List<MessageBubbles.Drawn> drawn = new ArrayList<>();
    private final MessageMenu messageMenu = new MessageMenu();
    private final MessageActions actions;
    private final ConfirmBox confirmBox = new ConfirmBox();
    private final ReplyStrip replyStrip = new ReplyStrip();
    private final PinnedBar pinnedBar = new PinnedBar();
    private final Selection selection = new Selection();
    // к какому сообщению прокрутить на следующем кадре и какое подсвечено
    private String jumpKey;
    // курсор на холсте в этом кадре: полосам, которые рисуются глубоко внутри
    private int lastMouseX;
    private int lastMouseY;
    private String highlightKey;
    private long highlightAt;
    private final ImageViewer viewer = new ImageViewer();
    private final MediaActions media;
    private final AttachmentStrip attachment;
    private final GifPicker gifPicker;
    private final ForwardPicker forwardPicker = new ForwardPicker();
    private final Forwarder forwarder;
    // почему сообщение не ушло: подпись над полем ввода
    private String warning = "";
    private long warningAt;
    private final Animated windowOpen = new Animated(0, Motion.SLOW);
    private final Animated chatScrollView = new Animated(0, Motion.FAST);
    private final Animated listScrollView = new Animated(0, Motion.FAST);

    private String partner;
    // список показывает архив вместо обычных чатов
    private boolean archiveView;
    private int chatScroll;
    private int listScroll;
    private int shownCount;
    // сколько последних сообщений чата лента раскладывает сейчас; растёт порциями при прокрутке вверх и переходе к старому
    private int historyWindow = HISTORY_PAGE;

    private int pixelScale = 1;
    private float scale = 1;
    private float offsetX;
    private float offsetY;

    public MessengerScreen(String partner, Context context) {
        super(Component.literal("Сообщения"));
        this.context = context;
        this.messages = context.messages();
        this.ui = context.ui();
        this.addons = ui.addons();
        this.announcements = ui.announcements();
        this.chatMedia = ui.media(context.route());
        this.bubbleContext = new BubbleContext(chatMedia, addons, announcements, messages::receipt, this::authorPrefix);
        this.groupCreator = new GroupCreator(chatMedia, this::groupCreated, this::warn);
        this.attachment = new AttachmentStrip(ui.images());
        this.gifPicker = new GifPicker(ui.gifs(), ui.images());
        this.media = new MediaActions(context.route(), ui.gifs(), this::warn, this::warnInfo);
        this.forwarder = new Forwarder(context.route(), this::warn, this::warnInfo);
        this.actions = new MessageActions(context.route(), messages, media, this::warn, this::warnInfo, new ActionsHost());
        this.settingsPanel = new MessengerSettingsPanel(context.alerts(),
                new ProfileSettingsHost(context.route(), this::warn));
        this.profilePanel = new ProfilePanel(context.route(), chatMedia, messages, context.friends()::skinOf, new ProfileHost());
        announcements.refresh();
        input = new TextFieldWidget(null, "Написать сообщение…").withMaxLength(MAX_TEXT).withoutFrame().withMultiline();
        int barY = HEIGHT - PAD - INPUT_HEIGHT;
        // скрепка — слева внутри полосы: выбранная картинка встаёт над полем и уйдёт вместе с текстом
        IconButtonWidget attach = new IconButtonWidget(Icon.IMAGE, () -> media.choose(this.partner, file -> {
            attachment.stage(file);
            input.focus();
        }));
        attach.setBounds(BAR_X + 2, barY, BAR_ICON, INPUT_HEIGHT);
        composer.addChild(attach);
        // справа внутри полосы: кнопки дополнений и избранные гифки
        List<ComposeAction> actions = addons.composeActions();
        int icons = actions.size() + 2;
        int iconsX = BAR_X + BAR_WIDTH - 2 - icons * BAR_ICON;
        for (int i = 0; i < actions.size(); i++) {
            ComposeAction action = actions.get(i);
            int buttonX = iconsX + i * BAR_ICON;
            IconButtonWidget button = new IconButtonWidget(action.icon(), () -> openCompose(action, buttonX));
            button.setBounds(buttonX, barY, BAR_ICON, INPUT_HEIGHT);
            composer.addChild(button);
        }
        IconButtonWidget gifs = new IconButtonWidget(Icon.STAR, this::toggleGifs);
        gifs.setBounds(iconsX + actions.size() * BAR_ICON, barY, BAR_ICON, INPUT_HEIGHT);
        composer.addChild(gifs);
        // микрофон: голосовое другу по сети
        IconButtonWidget mic = new IconButtonWidget(Icon.MIC, this::toggleVoice);
        mic.setBounds(iconsX + (actions.size() + 1) * BAR_ICON, barY, BAR_ICON, INPUT_HEIGHT);
        composer.addChild(mic);
        inputX = BAR_X + 2 + BAR_ICON + 2;
        inputWidth = iconsX - inputX - 2;
        input.setBounds(inputX, barY, inputWidth, INPUT_HEIGHT);
        search.setBounds(LIST_X + PAD, PAD, LIST_WIDTH - PAD * 2, FIELD_HEIGHT);
        ButtonWidget send = new ButtonWidget(Icon.SEND, this::send);
        send.setBounds(WIDTH - PAD - SEND_WIDTH, HEIGHT - PAD - INPUT_HEIGHT, SEND_WIDTH, INPUT_HEIGHT);
        ButtonWidget close = new ButtonWidget(Icon.CLOSE, this::onClose);
        close.setBounds(WIDTH - PAD - 16, (HEADER_HEIGHT - 16) / 2, 16, 16);
        root.setBounds(0, 0, WIDTH, HEIGHT);
        root.addChild(search);
        root.addChild(close);
        composer.setBounds(0, 0, WIDTH, HEIGHT);
        composer.addChild(input);
        composer.addChild(send);
        String first = partner != null ? partner : messages.partners().stream().findFirst().orElse(null);
        select(first);
        windowOpen.setTarget(1);
        UiSounds.play(UiSound.MENU_OPEN);
    }

    @Override
    protected void init() {
        Window window = minecraft.getWindow();
        int fitX = window.getWidth() / (WIDTH + SCREEN_MARGIN);
        int fitY = window.getHeight() / (HEIGHT + SCREEN_MARGIN);
        pixelScale = Math.max(1, Math.min(fitX, fitY));
        float guiScale = (float) window.getGuiScale();
        scale = pixelScale / guiScale;
        offsetX = (window.getWidth() - WIDTH * pixelScale) / 2f / guiScale;
        offsetY = (window.getHeight() - HEIGHT * pixelScale) / 2f / guiScale;
        if (partner != null && !isReadOnly()) {
            input.focus();
        }
    }

    private void select(String chat) {
        messageMenu.close();
        confirmBox.close();
        replyStrip.clear();
        selection.clear();
        pinnedBar.reset();
        jumpKey = null;
        highlightKey = null;
        forwardPicker.close();
        viewer.close();
        profilePanel.close();
        gifPicker.close();
        attachment.clear();
        messages.close();
        voiceStrip.cancel();
        // черновик уходящего чата сохраняется, у нового — подставляется
        messages.setDraft(partner, input.getText());
        partner = chat;
        input.setText(messages.draft(chat));
        pending.clear();
        appearAt.clear();
        chatScroll = 0;
        chatScrollView.snap(0);
        historyWindow = HISTORY_PAGE;
        if (chat != null) {
            if (!ANNOUNCEMENTS.equals(chat)) {
                messages.open(chat);
                markRead(messages.chat(chat));
            }
            shownCount = chatMessages(chat).size();
        }
    }

    // собеседникам по сети — «прочитано» на то, что игрок сейчас видит (если он не выключил это в настройках)
    private void markRead(List<PrivateMessage> seen) {
        if (partner != null && !isAnnouncements() && context.alerts().readReceipts() && !seen.isEmpty()) {
            int from = Math.max(0, seen.size() - READ_WINDOW);
            context.route().markRead(partner, seen.subList(from, seen.size()));
        }
    }

    private boolean isAnnouncements() {
        return ANNOUNCEMENTS.equals(partner);
    }

    private boolean isOfficial() {
        return PrivateMessages.isOfficial(partner);
    }

    // сюда писать нельзя: объявления
    private boolean isReadOnly() {
        return isAnnouncements();
    }

    private List<PrivateMessage> chatMessages(String chat) {
        return ANNOUNCEMENTS.equals(chat) ? announcementList() : messages.chat(chat);
    }

    private PrivateMessage lastMessage(String entry) {
        return ANNOUNCEMENTS.equals(entry) ? announcementList().getLast() : messages.last(entry);
    }

    private List<PrivateMessage> announcementList() {
        List<PrivateMessage> loaded = announcements.messages();
        return loaded.isEmpty() ? ANNOUNCEMENTS_EMPTY : loaded;
    }

    private boolean isNotes() {
        return PrivateMessages.NOTES.equals(partner);
    }

    private List<String> listEntries() {
        String typed = search.getText().strip();
        String query = typed.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        if (archiveView) {
            result.add(ARCHIVE_BACK);
            addMatching(result, messages.archivedPartners(), query);
            return result;
        }
        if (query.isEmpty() && !messages.archivedPartners().isEmpty()) {
            result.add(ARCHIVE_ROW);
        }
        if (query.isEmpty() || "объявления".contains(query)) {
            result.add(ANNOUNCEMENTS);
        }
        if (query.isEmpty() || "избранное".contains(query)) {
            result.add(PrivateMessages.NOTES);
        }
        addMatching(result, messages.partners(), query);
        if (!query.isEmpty()) {
            // поиск находит и чаты из архива
            addMatching(result, messages.archivedPartners(), query);
            // ник, с которым ещё нет переписки, — сразу «Написать»
            boolean known = result.stream().anyMatch(entry -> messages.displayName(entry).equalsIgnoreCase(typed));
            if (!known && NICK.matcher(typed).matches()) {
                result.add(WRITE_PREFIX + typed);
            }
        }
        return result;
    }

    private void addMatching(List<String> result, List<String> chats, String query) {
        for (String name : chats) {
            if (query.isEmpty() || messages.displayName(name).toLowerCase(Locale.ROOT).contains(query)) {
                result.add(name);
            }
        }
    }

    private static boolean isListRow(String entry) {
        return ARCHIVE_ROW.equals(entry) || ARCHIVE_BACK.equals(entry) || entry.startsWith(WRITE_PREFIX);
    }

    // служебная строка: значок и подпись вместо чата
    private void drawListRow(Gfx g, String entry, int rowY, int mouseX, int mouseY) {
        int x = LIST_X + 4;
        int width = LIST_WIDTH - 8;
        if (inside(mouseX, mouseY, x, rowY, width, ROW_HEIGHT)) {
            Draw.roundedRect(g, x, rowY, width, ROW_HEIGHT, Theme.RADIUS, Theme.CARD_HOVER);
        }
        int faceX = x + 6;
        int faceY = rowY + (ROW_HEIGHT - ROW_FACE) / 2;
        float textX = faceX + ROW_FACE + 7;
        int textWidth = (int) (x + width - 6 - textX);
        if (ARCHIVE_BACK.equals(entry)) {
            drawIconTile(g, Icon.ARROW_LEFT, ARCHIVE_COLOR, faceX, faceY, ROW_FACE);
            Draw.text(g, "Архив", textX, rowY + 6, Theme.TEXT, TextStyle.BODY_MEDIUM);
            Draw.text(g, Draw.trim("Назад ко всем чатам", textWidth), textX, rowY + 17, Theme.TEXT_SOFT, TextStyle.SMALL);
            return;
        }
        if (ARCHIVE_ROW.equals(entry)) {
            drawIconTile(g, Icon.ARCHIVE, ARCHIVE_COLOR, faceX, faceY, ROW_FACE);
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
            return;
        }
        String name = entry.substring(WRITE_PREFIX.length());
        drawFace(g, context.friends().skinOf(name), faceX, faceY, ROW_FACE);
        Draw.text(g, Draw.trim(name, textWidth), textX, rowY + 6, Theme.TEXT, TextStyle.BODY_MEDIUM);
        Draw.text(g, Draw.trim("Написать сообщение", textWidth), textX, rowY + 17, Theme.ACCENT, TextStyle.SMALL);
    }

    private static void drawIconTile(Gfx g, Icon icon, int color, float x, float y, int size) {
        Draw.roundedRect(g, x, y, size, size, size / 2f, color);
        float iconSize = size * 0.55f;
        Draw.icon(g, icon, x + (size - iconSize) / 2, y + (size - iconSize) / 2, iconSize, 0xFFFFFFFF);
    }

    // клик по служебной строке
    private void clickListRow(String entry) {
        UiSounds.play(UiSound.CLICK);
        listScroll = 0;
        listScrollView.snap(0);
        if (ARCHIVE_ROW.equals(entry) || ARCHIVE_BACK.equals(entry)) {
            archiveView = ARCHIVE_ROW.equals(entry);
            return;
        }
        String name = entry.substring(WRITE_PREFIX.length());
        search.setText("");
        startChat(name);
    }

    // переписка с игроком по нику из поиска: игрок есть в сети AFriends — чат по сети со сквозным шифрованием,
    // нет — как раньше, /msg этого сервера
    private void startChat(String name) {
        archiveView = false;
        ProfileRoute profile = context.route().profile();
        Runnable open = () -> {
            messages.ensureChat(name);
            select(name);
            input.focus();
        };
        if (profile == null) {
            open.run();
            return;
        }
        warnInfo("Ищу " + name + " в сети AFriends…");
        profile.startChat(name, () -> {
            open.run();
            warnInfo("Чат с " + name + " — через сеть AFriends, сквозное шифрование");
        }, () -> {
            open.run();
            warnInfo(name + " нет в сети AFriends — сообщение уйдёт через /msg этого сервера");
        }, this::warn);
    }

    private ChatMenu.Item archiveItem(String chat) {
        boolean inArchive = messages.isArchived(chat);
        return new ChatMenu.Item(Icon.ARCHIVE, inArchive ? "Вернуть из архива" : "В архив", false, () -> {
            messages.toggleArchived(chat);
            warnInfo(inArchive ? "Чат возвращён из архива" : "Чат в архиве — он вернётся, когда придёт сообщение");
        });
    }

    @Override
    protected void drawBackground(Gfx g, int mouseX, int mouseY, float partialTick) {
        float shown = windowOpen.get();
        MenuBlur.request(shown);
        g.blurBeforeThisStratum();
        Draw.pushAlpha(shown);
        Draw.backdrop(g, width, height, Theme.DIM, Theme.DIM_VIGNETTE, Theme.DIM_GRAIN);
        Draw.popAlpha();
    }

    @Override
    protected void draw(Gfx g, int mouseX, int mouseY, float partialTick) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(offsetX, offsetY);
        pose.scale(scale, scale);
        Draw.setPixelScale(pixelScale);
        int canvasX = (int) toCanvasX(mouseX);
        int canvasY = (int) toCanvasY(mouseY);
        lastMouseX = canvasX;
        lastMouseY = canvasY;
        int hoverX = menu.isOpen() || messageMenu.isOpen() || confirmBox.isOpen() ? -1000 : canvasX;

        float shown = windowOpen.get();
        Draw.pushAlpha(shown);
        Draw.pushOffset(g, 0, (1 - shown) * Motion.RISE);
        Draw.shadow(g, 0, 4, WIDTH, HEIGHT, Theme.PANEL_RADIUS, 24, Theme.SHADOW);
        Draw.gradient(g, 0, 0, WIDTH, HEIGHT, Theme.PANEL_RADIUS, Theme.PANEL_TOP, Theme.PANEL_BOTTOM);
        Draw.border(g, 0, 0, WIDTH, HEIGHT, Theme.PANEL_RADIUS, Theme.BORDER);

        Draw.rect(g, LIST_X, 1, 1, HEIGHT - 2, Theme.BORDER);
        Draw.rect(g, CHAT_X, 1, 1, HEIGHT - 2, Theme.BORDER);

        // пока окно открыто, объявления подтягиваются сами (запрос не чаще раза в 15 секунд)
        announcements.refresh();
        updateComposerLayout();
        drawRail(g, hoverX, canvasY);
        drawList(g, hoverX, canvasY);
        drawChat(g);
        root.render(g, hoverX, canvasY);
        if (isAnnouncements()) {
            drawReadOnly(g, "Только чтение — сюда приходят новости мода");
        } else if (partner != null) {
            drawBar(g, hoverX, canvasY);
            composer.render(g, hoverX, canvasY);
            voiceStrip.tick();
            voiceStrip.draw(g, BAR_X, HEIGHT - PAD - INPUT_HEIGHT, BAR_WIDTH, INPUT_HEIGHT);
        }
        g.nextStratum();
        drawRailTooltip(g, hoverX, canvasY);
        menu.draw(g, canvasX, canvasY);
        messageMenu.draw(g, canvasX, canvasY);
        gifPicker.draw(g, canvasX, canvasY);
        forwardPicker.draw(g, canvasX, canvasY, this::chatName, this::drawEntryTile);
        groupEditor.draw(g, canvasX, canvasY);
        pollComposer.draw(g, canvasX, canvasY);
        settingsPanel.draw(g, canvasX, canvasY);
        groupCreator.draw(g, canvasX, canvasY);
        profilePanel.draw(g, canvasX, canvasY);
        if (viewer.isOpen()) {
            g.nextStratum();
            viewer.draw(g, chatMedia, WIDTH, HEIGHT, canvasX, canvasY);
        }
        if (confirmBox.isOpen()) {
            g.nextStratum();
            confirmBox.draw(g, WIDTH, HEIGHT, canvasX, canvasY);
        }
        Draw.popOffset(g);
        Draw.popAlpha();
        pose.popMatrix();
    }

    private static int railButtonY(int index) {
        return PAD + index * RAIL_STEP;
    }

    private void drawRail(Gfx g, int mouseX, int mouseY) {
        RailAction[] actions = RailAction.values();
        for (int i = 0; i < actions.length; i++) {
            int x = (RAIL_WIDTH - RAIL_BUTTON) / 2;
            int y = railButtonY(i);
            boolean hovered = inside(mouseX, mouseY, x, y, RAIL_BUTTON, RAIL_BUTTON);
            boolean active = actions[i] == RailAction.NOTES ? isNotes() : actions[i] == RailAction.ALL_CHATS && !isNotes();
            int fill = active ? Theme.ACCENT : hovered ? Theme.CARD_HOVER : Colors.fade(Theme.CARD, 0);
            Draw.roundedRect(g, x, y, RAIL_BUTTON, RAIL_BUTTON, Theme.RADIUS, fill);
            int color = active ? Theme.ACCENT_TEXT : hovered ? Theme.TEXT : Theme.TEXT_SOFT;
            Draw.icon(g, railIcon(actions[i]), x + (RAIL_BUTTON - Theme.ICON) / 2f, y + (RAIL_BUTTON - Theme.ICON) / 2f,
                    Theme.ICON, color);
            if (i == 0) {
                Draw.rect(g, 8, y + RAIL_BUTTON + 3, RAIL_WIDTH - 16, 1, Theme.BORDER);
            }
        }
        // настройки мессенджера — внизу, как в Telegram
        int x = (RAIL_WIDTH - RAIL_BUTTON) / 2;
        int y = settingsButtonY();
        boolean hovered = inside(mouseX, mouseY, x, y, RAIL_BUTTON, RAIL_BUTTON);
        boolean active = settingsPanel.isOpen();
        Draw.roundedRect(g, x, y, RAIL_BUTTON, RAIL_BUTTON, Theme.RADIUS,
                active ? Theme.ACCENT : hovered ? Theme.CARD_HOVER : Colors.fade(Theme.CARD, 0));
        Draw.icon(g, Icon.SETTINGS, x + (RAIL_BUTTON - Theme.ICON) / 2f, y + (RAIL_BUTTON - Theme.ICON) / 2f, Theme.ICON,
                active ? Theme.ACCENT_TEXT : hovered ? Theme.TEXT : Theme.TEXT_SOFT);
    }

    private static int settingsButtonY() {
        return HEIGHT - PAD - RAIL_BUTTON;
    }

    private static Icon railIcon(RailAction action) {
        return switch (action) {
            case READ_ALL -> Icon.CHECK_SQUARE;
            case ALL_CHATS -> Icon.MESSAGE;
            case NOTES -> Icon.HEART;
            case NEW_GROUP -> Icon.USERS;
        };
    }

    private static String railLabel(RailAction action) {
        return switch (action) {
            case READ_ALL -> "Прочитать всё";
            case ALL_CHATS -> "Все чаты";
            case NOTES -> "Избранное";
            case NEW_GROUP -> "Новая группа";
        };
    }

    private void drawRailTooltip(Gfx g, int mouseX, int mouseY) {
        if (inside(mouseX, mouseY, (RAIL_WIDTH - RAIL_BUTTON) / 2, settingsButtonY(), RAIL_BUTTON, RAIL_BUTTON)) {
            drawTooltip(g, "Настройки мессенджера", settingsButtonY());
        }
        RailAction[] actions = RailAction.values();
        for (int i = 0; i < actions.length; i++) {
            int y = railButtonY(i);
            if (inside(mouseX, mouseY, (RAIL_WIDTH - RAIL_BUTTON) / 2, y, RAIL_BUTTON, RAIL_BUTTON)) {
                drawTooltip(g, railLabel(actions[i]), y);
            }
        }
    }

    private static void drawTooltip(Gfx g, String label, int y) {
        float width = Draw.width(label) + 12;
        Draw.shadow(g, RAIL_WIDTH + 2, y + 4, width, 16, Theme.RADIUS, 8, Theme.SHADOW);
        Draw.box(g, RAIL_WIDTH + 2, y + 4, width, 16, Theme.RADIUS, Theme.POPUP, Theme.BORDER_HOVER);
        Draw.text(g, label, RAIL_WIDTH + 8, y + 8, Theme.TEXT);
    }

    private static int listTop() {
        return PAD + FIELD_HEIGHT + 6;
    }

    private void drawList(Gfx g, int mouseX, int mouseY) {
        List<String> entries = listEntries();
        int top = listTop();
        int bottom = HEIGHT - 4;
        int maxScroll = Math.max(0, entries.size() * ROW_HEIGHT - (bottom - top));
        if (listScroll > maxScroll) {
            listScroll = maxScroll;
            listScrollView.setTarget(listScroll);
        }
        int shownScroll = Math.round(listScrollView.get());
        g.enableScissor(LIST_X + 1, top, CHAT_X, bottom);
        for (int i = 0; i < entries.size(); i++) {
            int rowY = top + i * ROW_HEIGHT - shownScroll;
            if (rowY + ROW_HEIGHT < top || rowY > bottom) {
                continue;
            }
            drawEntry(g, entries.get(i), rowY, mouseX, mouseY);
        }
        g.disableScissor();
    }

    private void drawEntry(Gfx g, String entry, int rowY, int mouseX, int mouseY) {
        if (isListRow(entry)) {
            drawListRow(g, entry, rowY, mouseX, mouseY);
            return;
        }
        int x = LIST_X + 4;
        int width = LIST_WIDTH - 8;
        boolean hovered = inside(mouseX, mouseY, x, rowY, width, ROW_HEIGHT);
        boolean selected = entry.equalsIgnoreCase(partner == null ? "" : partner);
        if (selected) {
            Draw.roundedRect(g, x, rowY, width, ROW_HEIGHT, Theme.RADIUS, Theme.CARD_SELECTED);
            Draw.roundedRect(g, x, rowY + 6, 2, ROW_HEIGHT - 12, 1, Theme.ACCENT);
        } else if (hovered) {
            Draw.roundedRect(g, x, rowY, width, ROW_HEIGHT, Theme.RADIUS, Theme.CARD_HOVER);
        }
        boolean announcements = ANNOUNCEMENTS.equals(entry);
        boolean notes = announcements || PrivateMessages.NOTES.equals(entry);
        int faceX = x + 6;
        int faceY = rowY + (ROW_HEIGHT - ROW_FACE) / 2;
        drawEntryTile(g, entry, faceX, faceY, ROW_FACE);

        float textX = faceX + ROW_FACE + 7;
        PrivateMessage last = lastMessage(entry);
        String date = last == null ? "" : formatDate(last.time());
        float dateWidth = Draw.width(date, TextStyle.SMALL);
        boolean pinned = !notes && messages.isPinned(entry);
        boolean muted = !notes && context.alerts().isMuted(entry);
        float pinSpace = (pinned ? Theme.ICON_SMALL + 3 : 0) + (muted ? Theme.ICON_SMALL + 3 : 0);
        int nameWidth = (int) (x + width - 6 - textX - dateWidth - pinSpace - 4);
        String title = announcements ? ANNOUNCEMENTS_TITLE : notes ? "Избранное" : messages.displayName(entry);
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

    private static void drawAnnouncementsTile(Gfx g, float x, float y, int size) {
        Draw.roundedRect(g, x, y, size, size, Theme.RADIUS, ANNOUNCEMENTS_COLOR);
        float icon = size * 0.55f;
        Draw.icon(g, Icon.BELL, x + (size - icon) / 2, y + (size - icon) / 2, icon, 0xFFFFFFFF);
    }

    // значок чата: объявления, AFriends, избранное или голова игрока (список чатов и окно пересылки)
    private void drawEntryTile(Gfx g, String entry, float x, float y, int size) {
        if (ANNOUNCEMENTS.equals(entry)) {
            drawAnnouncementsTile(g, x, y, size);
        } else if (PrivateMessages.isOfficial(entry)) {
            drawOfficialTile(g, x, y, size);
        } else if (PrivateMessages.NOTES.equals(entry)) {
            drawNotesTile(g, x, y, size);
        } else if (PrivateMessages.isGroup(entry)) {
            drawGroupTile(g, entry, x, y, size);
        } else {
            drawFace(g, context.friends().skinOf(entry), (int) x, (int) y, size);
            if (OnlineDot.isOnline(addons, entry)) {
                OnlineDot.draw(g, x, y, size);
            }
        }
    }

    // аватарка группы, а пока её нет — синий кружок со значком
    private void drawGroupTile(Gfx g, String chat, float x, float y, int size) {
        PanelKit.groupTile(g, messages.avatar(chat), chatMedia, x, y, size);
    }

    private static void drawOfficialTile(Gfx g, float x, float y, int size) {
        Draw.roundedRect(g, x, y, size, size, Theme.RADIUS, OFFICIAL_COLOR);
        float icon = size * 0.55f;
        Draw.icon(g, Icon.DROPLET, x + (size - icon) / 2, y + (size - icon) / 2, icon, 0xFFFFFFFF);
    }

    /** Вместо поля ввода в чатах только для чтения. */
    private void drawReadOnly(Gfx g, String text) {
        float y = HEIGHT - PAD - INPUT_HEIGHT;
        Draw.box(g, CHAT_X + PAD, y, CHAT_WIDTH - PAD * 2, INPUT_HEIGHT, Theme.RADIUS, Theme.CARD, Theme.BORDER);
        float textX = CHAT_X + PAD + (CHAT_WIDTH - PAD * 2 - Theme.ICON_SMALL - 5 - Draw.width(text)) / 2f;
        Draw.icon(g, Icon.LOCK, textX, y + (INPUT_HEIGHT - Theme.ICON_SMALL) / 2f, Theme.ICON_SMALL, Theme.TEXT_DIM);
        Draw.text(g, text, textX + Theme.ICON_SMALL + 5, y + (INPUT_HEIGHT - 8) / 2f, Theme.TEXT_DIM);
    }

    private static void drawNotesTile(Gfx g, float x, float y, int size) {
        Draw.roundedRect(g, x, y, size, size, Theme.RADIUS, NOTES_COLOR);
        float icon = size * 0.55f;
        Draw.icon(g, Icon.HEART, x + (size - icon) / 2, y + (size - icon) / 2, icon, 0xFFFFFFFF);
    }

    private static void drawFace(Gfx g, PlayerSkin skin, int x, int y, int size) {
        if (skin != null) {
            g.face(skin, x, y, size);
        } else {
            Draw.gradient(g, x, y, size, size, Theme.RADIUS - 1, Theme.TILE_ON, Theme.TILE);
            float icon = size * 0.5f;
            Draw.icon(g, Icon.USER, x + (size - icon) / 2, y + (size - icon) / 2, icon, Theme.TEXT_DIM);
        }
    }

    private static String formatDate(long time) {
        LocalDate date = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate();
        if (date.equals(LocalDate.now())) {
            return TIME.format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()));
        }
        return DATE.format(date);
    }

    private void drawChat(Gfx g) {
        if (partner == null) {
            float centerX = CHAT_X + CHAT_WIDTH / 2f;
            float drift = (Easing.pulse(Motion.now(), Theme.IDLE_PERIOD) - 0.5f) * 3;
            Draw.icon(g, Icon.MESSAGE, centerX - 10, HEIGHT / 2f - 24 + drift, 20, Theme.TEXT_FAINT);
            Draw.textCentered(g, "Выбери чат слева", centerX, HEIGHT / 2f + 2, Theme.TEXT_DIM);
            return;
        }
        drawHeader(g);
        drawPinnedBar(g);
        drawMessages(g);
        drawWarning(g);
    }

    private List<PrivateMessage> pinnedMessages() {
        return partner == null || isAnnouncements() ? List.of() : messages.pinnedMessages(partner);
    }

    // верх ленты сообщений: под шапкой, а если есть закреп — под его плашкой
    private int messagesTop() {
        return HEADER_HEIGHT + 4 + (pinnedMessages().isEmpty() ? 0 : PinnedBar.HEIGHT);
    }

    // рисуется и при пустом списке: так плашка забывает открепленное и не ловит по нему клики
    private void drawPinnedBar(Gfx g) {
        int hover = messageMenu.isOpen() || menu.isOpen() || confirmBox.isOpen() ? -1000 : lastMouseX;
        pinnedBar.draw(g, pinnedMessages(), CHAT_X + 1, HEADER_HEIGHT + 1, CHAT_WIDTH - 2, hover, lastMouseY);
    }

    // верх области ввода: если картинка прикреплена, её превью стоит над полосой
    private int composerTop() {
        if (isReadOnly()) {
            return HEIGHT - PAD - INPUT_HEIGHT;
        }
        return HEIGHT - PAD - INPUT_HEIGHT - inputExtra - (attachment.isStaged() ? AttachmentStrip.HEIGHT + 4 : 0)
                - (replyStrip.isActive() ? ReplyStrip.HEIGHT + 4 : 0);
    }

    // поле ввода растёт вверх по числу строк (до MAX_INPUT_LINES), кнопки остаются внизу
    private void updateComposerLayout() {
        int lines = Math.min(MAX_INPUT_LINES, input.lineCount());
        inputExtra = (lines - 1) * TextFieldWidget.lineHeight();
        input.setBounds(inputX, HEIGHT - PAD - INPUT_HEIGHT - inputExtra, inputWidth, INPUT_HEIGHT + inputExtra);
    }

    private static boolean isShiftDown() {
        return Compat.isKeyDown(InputConstants.KEY_LSHIFT) || Compat.isKeyDown(InputConstants.KEY_RSHIFT);
    }

    // общая рамка полосы ввода: внутри неё скрепка, поле и значки
    private void drawBar(Gfx g, int mouseX, int mouseY) {
        // плашка растёт вверх вместе с многострочным полем; значки и «отправить» остаются внизу
        int barY = HEIGHT - PAD - INPUT_HEIGHT - inputExtra;
        int border = input.isFocused() ? Theme.BORDER_SELECTED : Theme.BORDER;
        Draw.box(g, BAR_X, barY, BAR_WIDTH, INPUT_HEIGHT + inputExtra, Theme.RADIUS, Theme.CARD, border);
        // над полосой: сначала ответ, над ним прикреплённая картинка
        int replyY = barY - ReplyStrip.HEIGHT - 4;
        replyStrip.draw(g, BAR_X, replyY, BAR_WIDTH + SEND_WIDTH + 4, mouseX, mouseY);
        int attachY = (replyStrip.isActive() ? replyY : barY) - AttachmentStrip.HEIGHT - 4;
        attachment.draw(g, BAR_X, attachY, BAR_WIDTH + SEND_WIDTH + 4, mouseX, mouseY);
    }

    // голосовые — только другу по сети: им нужно сквозное шифрование вложений
    private boolean canSendVoice() {
        return partner != null && !isReadOnly() && !isNotes() && !isOfficial() && !PrivateMessages.isGroup(partner)
                && context.route().supportsMedia(partner);
    }

    private void toggleVoice() {
        if (!voiceStrip.isRecording() && !canSendVoice()) {
            warn("Голосовые можно отправлять только другу по сети");
            return;
        }
        voiceStrip.toggle();
        UiSounds.play(UiSound.CLICK);
    }

    private void sendVoice(VoiceClip clip) {
        if (!canSendVoice()) {
            return;
        }
        String target = partner;
        context.route().sendVoice(target, clip.encode(), clip.centiseconds(), replyStrip.reply(), this::warn).ifPresentOrElse(this::warn,
                () -> {
                    replyStrip.clear();
                    warnInfo("Голосовое отправляется…");
                });
    }

    private void toggleGifs() {
        if (!media.available(partner)) {
            warn("Гифки можно отправлять только друзьям по сети");
            return;
        }
        gifPicker.toggle(BAR_X + BAR_WIDTH, composerTop());
    }

    private void drawWarning(Gfx g) {
        if (warning.isEmpty() || System.currentTimeMillis() - warningAt > WARNING_MS) {
            return;
        }
        float width = Math.min(CHAT_WIDTH - PAD * 2, Draw.width(warning, TextStyle.SMALL) + 12);
        float x = CHAT_X + (CHAT_WIDTH - width) / 2f;
        float y = composerTop() - 18;
        Draw.box(g, x, y, width, 14, Theme.RADIUS, Theme.POPUP, DANGER);
        Draw.text(g, Draw.trim(warning, (int) width - 12, TextStyle.SMALL), x + 6, y + 4, DANGER, TextStyle.SMALL);
    }

    private void warn(String text) {
        warning = text;
        warningAt = System.currentTimeMillis();
        UiSounds.play(UiSound.CLICK);
    }

    private void drawHeader(Gfx g) {
        if (selection.isActive()) {
            selection.drawBar(g, CHAT_X + PAD + 2, 0, CHAT_WIDTH - PAD * 2 - 22, HEADER_HEIGHT, lastMouseX, lastMouseY);
            Draw.rect(g, CHAT_X + 1, HEADER_HEIGHT, CHAT_WIDTH - 2, 1, Theme.BORDER);
            return;
        }
        int faceX = CHAT_X + PAD + 2;
        int faceY = (HEADER_HEIGHT - HEADER_FACE) / 2;
        String title;
        String status;
        int statusColor = Theme.TEXT_SOFT;
        if (isAnnouncements()) {
            drawAnnouncementsTile(g, faceX, faceY, HEADER_FACE);
            title = ANNOUNCEMENTS_TITLE;
            status = "Новости от AFriends · только чтение";
        } else if (isOfficial()) {
            drawOfficialTile(g, faceX, faceY, HEADER_FACE);
            title = PrivateMessages.OFFICIAL_NAME;
            status = "Администрация мода · ответ видят только админы";
        } else if (isNotes()) {
            drawNotesTile(g, faceX, faceY, HEADER_FACE);
            title = "Избранное";
            status = "Твой личный чат для заметок";
        } else if (PrivateMessages.isGroup(partner)) {
            drawGroupTile(g, partner, faceX, faceY, HEADER_FACE);
            title = messages.displayName(partner);
            String network = context.route().headerLine(partner);
            status = network != null ? network : "Групповой чат";
        } else {
            drawFace(g, context.friends().skinOf(partner), faceX, faceY, HEADER_FACE);
            if (OnlineDot.isOnline(addons, partner)) {
                OnlineDot.draw(g, faceX, faceY, HEADER_FACE);
            }
            title = messages.displayName(partner);
            boolean offline = messages.offlineAt(partner) > 0;
            Friend friend = context.friends().find(partner);
            String network = context.route().headerLine(partner);
            status = network != null ? network : offline ? "Не в сети — сообщения не доставлены"
                    : friend == null ? "Не в друзьях · через /msg сервера" : "Друг · через /msg сервера";
            statusColor = offline && network == null ? DANGER : Theme.TEXT_SOFT;
        }
        float textX = faceX + HEADER_FACE + 8;
        Draw.text(g, Draw.trim(title, CHAT_WIDTH - 80), textX, 11, Theme.TEXT, TextStyle.TITLE);
        Draw.text(g, Draw.trim(status, CHAT_WIDTH - 80), textX, 25, statusColor, TextStyle.SMALL);
        Draw.rect(g, CHAT_X + 1, HEADER_HEIGHT, CHAT_WIDTH - 2, 1, Theme.BORDER);
    }

    private void drawMessages(Gfx g) {
        int top = messagesTop();
        int bottom = composerTop() - 6;
        List<PrivateMessage> chat = chatMessages(partner);
        trackNewMessages(chat);
        long now = System.currentTimeMillis();
        pending.removeIf(item -> !isFailed(item) && now - item.sentAt() > PENDING_TIMEOUT_MS);
        List<Pending> mine = pending.stream().filter(item -> item.partner().equalsIgnoreCase(partner)).toList();

        // переход к сообщению старше загруженной части: сначала догружаем её, прокрутка — когда сообщение в ленте
        if (jumpKey != null) {
            revealForJump(chat, jumpKey);
        }
        int from = Math.max(0, chat.size() - historyWindow);
        List<PrivateMessage> loaded = chat.subList(from, chat.size());
        List<PrivateMessage> shown = new ArrayList<>(loaded);
        mine.forEach(item -> shown.add(new PrivateMessage(true, item.text(), item.sentAt(), null, List.of(), null, null, null,
                item.reply())));
        if (shown.isEmpty()) {
            float centerX = CHAT_X + CHAT_WIDTH / 2f;
            String text = isNotes() ? "Пиши сюда заметки — их видишь только ты" : "Сообщений пока нет — напиши первым";
            Draw.textCentered(g, text, centerX, (top + bottom) / 2f, Theme.TEXT_SOFT);
            return;
        }
        // при выделении слева кружки: входящие сдвигаются вправо
        float left = CHAT_X + PAD + (selection.isActive() ? Selection.GUTTER : 0);
        float right = WIDTH - PAD;
        List<Row> rows = buildRows(bubbleContext, shown, right - left, loaded.size(), partner);
        cardButtons.clear();
        drawn.clear();
        float content = 0;
        for (Row row : rows) {
            content += row.height();
        }
        int maxScroll = (int) Math.max(0, content - (bottom - top));
        if (chatScroll > maxScroll) {
            chatScroll = maxScroll;
            chatScrollView.setTarget(chatScroll);
        }
        if (jumpKey != null) {
            jump(rows, content, top, bottom, maxScroll);
        }
        // докрутили почти до верха — следующая порция истории. Лента прижата к низу, поэтому старые сообщения
        // добавляются сверху и видимое место не прыгает
        if (from > 0 && chatScroll >= maxScroll - HISTORY_PRELOAD) {
            historyWindow += HISTORY_PAGE;
        }

        g.enableScissor(CHAT_X + 1, top, WIDTH - 1, bottom);
        float y = bottom - content + Math.round(chatScrollView.get());
        for (Row row : rows) {
            if (y + row.height() >= top && y <= bottom) {
                if (row.day() != null) {
                    drawDay(g, row.day(), y);
                } else {
                    boolean isPending = row.index() >= loaded.size();
                    Pending item = isPending ? mine.get(row.index() - loaded.size()) : null;
                    long start = isPending ? item.sentAt() : appearAt.getOrDefault(from + row.index(), 0L);
                    MessageBubbles.Stamp stamp = !isPending ? MessageBubbles.Stamp.TIME
                            : isFailed(item) ? MessageBubbles.Stamp.FAILED : MessageBubbles.Stamp.PENDING;
                    MessageBubbles.Drawn bubble = drawAnimated(g, row.bubble(), left, right, y, start, stamp, now, bubbleContext);
                    if (bubble.card() != null && y >= top && y + row.height() <= bottom) {
                        cardButtons.add(bubble.card());
                    }
                    if (!isPending) {
                        drawn.add(bubble);
                        drawMarks(g, bubble, now);
                    }
                }
            }
            y += row.height();
        }
        g.disableScissor();
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

    // прокрутить так, чтобы сообщение jumpKey оказалось посередине, и подсветить его
    private void jump(List<Row> rows, float content, int top, int bottom, int maxScroll) {
        String key = jumpKey;
        jumpKey = null;
        float offset = 0;
        for (Row row : rows) {
            if (row.bubble() != null && key.equals(row.bubble().message().key())) {
                float rowCenter = offset + row.height() / 2f;
                chatScroll = Math.round(Math.max(0, Math.min(maxScroll, content - rowCenter - (bottom - top) / 2f)));
                chatScrollView.setTarget(chatScroll);
                highlightKey = key;
                highlightAt = System.currentTimeMillis();
                return;
            }
            offset += row.height();
        }
        warnInfo("Сообщения уже нет: оно удалено");
    }

    // расширить загруженную часть так, чтобы сообщение key в неё попало (с запасом сверху)
    private void revealForJump(List<PrivateMessage> chat, String key) {
        for (int i = chat.size() - 1; i >= 0; i--) {
            if (key.equals(chat.get(i).key())) {
                historyWindow = Math.max(historyWindow, chat.size() - i + HISTORY_PAGE / 2);
                return;
            }
        }
    }

    private static List<Row> buildRows(BubbleContext ctx, List<PrivateMessage> shown, float areaWidth, int firstPending, String partner) {
        List<MessageBubbles.Bubble> bubbles = MessageBubbles.layout(ctx, shown, areaWidth, firstPending, partner);
        List<Row> rows = new ArrayList<>();
        LocalDate previous = null;
        for (int i = 0; i < bubbles.size(); i++) {
            LocalDate day = Instant.ofEpochMilli(shown.get(i).time()).atZone(ZoneId.systemDefault()).toLocalDate();
            if (!day.equals(previous)) {
                rows.add(new Row(dayLabel(day), null, -1, SEPARATOR_HEIGHT));
                previous = day;
            }
            rows.add(new Row(null, bubbles.get(i), i, bubbles.get(i).height() + MessageBubbles.GAP));
        }
        return rows;
    }

    private static String dayLabel(LocalDate day) {
        LocalDate today = LocalDate.now();
        if (day.equals(today)) {
            return "Сегодня";
        }
        if (day.equals(today.minusDays(1))) {
            return "Вчера";
        }
        String label = day.getDayOfMonth() + " " + MONTHS[day.getMonthValue() - 1];
        return day.getYear() == today.getYear() ? label : label + " " + day.getYear();
    }

    private static void drawDay(Gfx g, String label, float y) {
        float width = Draw.width(label, TextStyle.SMALL) + 12;
        float x = CHAT_X + (CHAT_WIDTH - width) / 2f;
        Draw.box(g, x, y + 3, width, 11, 5.5f, Theme.CARD, Theme.BORDER);
        Draw.text(g, label, x + 6, y + 5.5f, Theme.TEXT_SOFT, TextStyle.SMALL);
    }

    private boolean isFailed(Pending item) {
        return messages.offlineAt(item.partner()) >= item.sentAt();
    }

    private void trackNewMessages(List<PrivateMessage> chat) {
        if (chat.size() == shownCount) {
            return;
        }
        if (chat.size() > shownCount) {
            markRead(chat.subList(shownCount, chat.size()));
        }
        long now = System.currentTimeMillis();
        for (int i = shownCount; i < chat.size(); i++) {
            PrivateMessage message = chat.get(i);
            long start = now;
            if (message.fromMe()) {
                Pending match = pending.stream()
                        .filter(item -> item.partner().equalsIgnoreCase(partner) && item.text().equals(message.text().strip()))
                        .findFirst().orElse(null);
                if (match != null) {
                    pending.remove(match);
                    start = match.sentAt();
                }
            }
            appearAt.put(i, start);
        }
        // новые сообщения не выталкивают из ленты старые, уже загруженные
        if (chat.size() > shownCount) {
            historyWindow += chat.size() - shownCount;
        }
        if (chatScroll < SCROLL_STEP) {
            chatScroll = 0;
            chatScrollView.setTarget(0);
        }
        shownCount = chat.size();
    }

    private static MessageBubbles.Drawn drawAnimated(Gfx g, MessageBubbles.Bubble bubble, float left, float right,
                                                     float y, long start, MessageBubbles.Stamp stamp, long now,
                                                     BubbleContext ctx) {
        float progress = start == 0 ? 1 : Math.min(1, (now - start) / (float) APPEAR_MS);
        float eased = Easing.outCubic(progress);
        float alpha = eased * (stamp == MessageBubbles.Stamp.TIME ? 1 : PENDING_ALPHA);
        if (stamp == MessageBubbles.Stamp.PENDING) {
            float age = (now - start) / (float) PENDING_TIMEOUT_MS;
            alpha *= age > 0.8f ? (1 - age) / 0.2f : 1;
        }
        float slideX = bubble.message().fromMe() ? (1 - eased) * SLIDE_X : 0;
        Draw.pushAlpha(alpha);
        Draw.pushOffset(g, slideX, (1 - eased) * SLIDE_Y);
        MessageBubbles.Drawn drawnBubble = MessageBubbles.draw(g, bubble, left, right, y, stamp, ctx);
        Draw.popOffset(g);
        Draw.popAlpha();
        return drawnBubble;
    }

    private void send() {
        if (voiceStrip.isRecording()) {
            voiceStrip.finish();
            return;
        }
        // больше трёх пустых строк подряд не уходит
        String text = MessageFormat.limitBlankLines(input.getText()).strip();
        // прикреплённая картинка уходит вместе с текстом: картинка сверху, текст подписью
        if (attachment.isStaged()) {
            if (partner != null && !isReadOnly() && media.send(partner, attachment.file(), text, replyStrip.reply())) {
                attachment.clear();
                replyStrip.clear();
                input.setText("");
            }
            return;
        }
        if (!text.isEmpty() && sendText(text)) {
            input.setText("");
        }
    }

    // false — не ушло: нет соединения или дополнение не пустило
    private boolean sendText(String text) {
        if (partner == null || isReadOnly()) {
            return false;
        }
        MessageReply reply = replyStrip.reply();
        if (isNotes()) {
            messages.addNote(new PrivateMessage(true, text, System.currentTimeMillis(), null, List.of(), null, null, null, reply));
        } else {
            // другу по сети — сквозным шифрованием через сервер сети, остальным — /msg
            Optional<String> problem = context.route().send(partner, text, null, reply, this::warn);
            if (problem.isPresent()) {
                warn(problem.get());
                return false;
            }

            pending.add(new Pending(partner, text, System.currentTimeMillis(), reply));
        }
        replyStrip.clear();
        warning = "";
        chatScroll = 0;
        chatScrollView.setTarget(0);
        UiSounds.play(UiSound.CLICK);
        return true;
    }

    // кнопка дополнения у поля ввода: список вариантов, выбранный уходит сообщением
    private void openCompose(ComposeAction action, int buttonX) {
        if (partner == null) {
            return;
        }
        List<ChatMenu.Item> items = new ArrayList<>();
        for (ComposeAction.Option option : addons.guard("composeAction", () -> action.options(partner), List.<ComposeAction.Option>of())) {
            items.add(new ChatMenu.Item(option.icon(), option.label(), false, () -> sendText(option.text())));
        }
        if (items.isEmpty()) {
            items.add(new ChatMenu.Item(Icon.INFO, addons.guard("composeAction", () -> action.empty(partner), ""), false, () -> { }));
        }
        String title = addons.guard("composeAction", () -> action.title(partner), "");
        menu.open(title, items, buttonX, HEIGHT - PAD - INPUT_HEIGHT, WIDTH, HEIGHT);
        UiSounds.play(UiSound.DROPDOWN_OPEN);
    }

    private void openMenu(String entry, double mouseX, double mouseY) {
        if (ANNOUNCEMENTS.equals(entry)) {
            return;
        }
        List<ChatMenu.Item> items = new ArrayList<>();
        // AFriends нельзя игнорировать, закрепить или удалить из друзей — только почистить у себя
        if (PrivateMessages.isOfficial(entry)) {
            items.add(new ChatMenu.Item(Icon.BRUSH, "Очистить чат", true, () -> {
                messages.clear(entry);
                if (isOfficial()) {
                    select(entry);
                }
            }));
            items.add(muteItem(entry));
            menu.open(PrivateMessages.OFFICIAL_NAME, items, (float) mouseX, (float) mouseY, WIDTH, HEIGHT);
            return;
        }
        if (PrivateMessages.NOTES.equals(entry)) {
            items.add(new ChatMenu.Item(Icon.BRUSH, "Очистить чат", true, () -> {
                messages.clear(PrivateMessages.NOTES);
                if (isNotes()) {
                    select(PrivateMessages.NOTES);
                }
            }));
            menu.open("Избранное", items, (float) mouseX, (float) mouseY, WIDTH, HEIGHT);
            return;
        }
        if (PrivateMessages.isGroup(entry)) {
            openGroupMenu(entry, items, mouseX, mouseY);
            return;
        }
        String name = messages.displayName(entry);
        FriendManager friends = context.friends();
        IgnoreList ignored = context.ignored();
        boolean friend = friends.find(name) != null;
        items.add(new ChatMenu.Item(friend ? Icon.USER_OFF : Icon.USERS, friend ? "Удалить из друзей" : "Добавить в друзья",
                friend, () -> {
                    if (friend) {
                        friends.remove(name);
                        addons.removed(name);
                    } else if (friends.add(name) == FriendManager.AddResult.ADDED) {
                        addons.added(name, this::warnInfo);
                    }
                    context.save().run();
                }));
        boolean isIgnored = ignored.isIgnored(name);
        items.add(new ChatMenu.Item(Icon.BAN, isIgnored ? "Не игнорировать" : "Игнорировать", false, () -> {
            ignored.toggle(name);
            context.save().run();
        }));
        addonItems(name, items);
        items.add(muteItem(name));
        items.add(archiveItem(name));
        boolean pinned = messages.isPinned(name);
        items.add(new ChatMenu.Item(Icon.PIN, pinned ? "Открепить чат" : "Закрепить чат", false, () -> messages.togglePin(name)));
        items.add(new ChatMenu.Item(Icon.BRUSH, "Очистить чат", false, () -> {
            messages.clear(name);
            if (name.equalsIgnoreCase(partner)) {
                select(partner);
            }
        }));
        items.add(new ChatMenu.Item(Icon.TRASH, "Удалить чат", true, () -> {
            messages.delete(name);
            if (name.equalsIgnoreCase(partner)) {
                select(null);
            }
        }));
        menu.open(name, items, (float) mouseX, (float) mouseY, WIDTH, HEIGHT);
    }

    // пункты аддонов из меню друга — и в меню переписки с ним (например, «Позвать в пати»)
    private void addonItems(String name, List<ChatMenu.Item> items) {
        for (FriendAction action : addons.actions()) {
            if (addons.guard("friendAction", () -> action.visible().test(name), false)) {
                items.add(new ChatMenu.Item(addons.guard("friendAction", action::icon, Icon.CUBE),
                        addons.guard("friendAction", () -> action.label().apply(name), ""),
                        addons.guard("friendAction", action::danger, false), () -> addons.run("friendAction", () -> action.action().accept(name))));
            }
        }
    }

    private GroupChats groups() {
        return context.route().groups();
    }

    // окна группы — только при живой сети: без неё состав не поменять
    // поле названия группы забирало ввод текста и, закрываясь, выключило его: возвращаем полю сообщения
    private boolean afterEditor(boolean handled) {
        if (!groupEditor.isOpen() && !pollComposer.isOpen() && !settingsPanel.isOpen() && !groupCreator.isOpen()
                && !profilePanel.isOpen()) {
            input.refocus();
        }
        return handled;
    }

    private void openGroupEditor(Runnable open) {
        GroupChats groups = groups();
        if (groups == null || !groups.available()) {
            warn("Группы работают через сеть друзей AFriends — она сейчас не подключена");
            return;
        }
        menu.close();
        open.run();
        UiSounds.play(UiSound.DROPDOWN_OPEN);
    }

    private void openGroupMenu(String chat, List<ChatMenu.Item> items, double mouseX, double mouseY) {
        GroupChats groups = groups();
        int perms = groups == null ? 0 : groups.myPerms(chat);
        items.add(new ChatMenu.Item(Icon.USERS, "Участники", false, () -> openGroupEditor(() ->
                profilePanel.open(chat, ProfilePanel.Tab.MEMBERS, WIDTH, HEIGHT))));
        if (GroupPermissions.has(perms, GroupPermissions.ADD)) {
            items.add(new ChatMenu.Item(Icon.PLUS, "Позвать друга", false, () -> openGroupEditor(() -> openInvite(chat))));
        }
        if (GroupPermissions.has(perms, GroupPermissions.TITLE)) {
            items.add(new ChatMenu.Item(Icon.EDIT, "Переименовать", false, () -> openGroupEditor(() ->
                    groupEditor.openRename(chat, messages.displayName(chat), WIDTH, HEIGHT))));
        }
        if (GroupPermissions.has(perms, GroupPermissions.POLLS)) {
            items.add(new ChatMenu.Item(Icon.BAR_CHART, "Создать опрос", false, () -> openGroupEditor(() ->
                    pollComposer.open(chat, WIDTH, HEIGHT))));
        }
        items.add(muteItem(chat));
        items.add(archiveItem(chat));
        boolean pinned = messages.isPinned(chat);
        items.add(new ChatMenu.Item(Icon.PIN, pinned ? "Открепить чат" : "Закрепить чат", false, () -> messages.togglePin(chat)));
        items.add(new ChatMenu.Item(Icon.BRUSH, "Очистить чат", false, () -> {
            messages.clear(chat);
            if (chat.equals(partner)) {
                select(chat);
            }
        }));
        items.add(new ChatMenu.Item(Icon.LOG_OUT, "Выйти из группы", true, () -> new GroupHost().leave(chat)));
        menu.open(messages.displayName(chat), items, (float) mouseX, (float) mouseY, WIDTH, HEIGHT);
    }

    // «Позвать друга»: друзья по сети, которых ещё нет в группе
    private void openInvite(String chat) {
        GroupChats groups = groups();
        List<String> inGroup = groups.members(chat);
        List<String> candidates = groups.friends().stream()
                .filter(friend -> inGroup.stream().noneMatch(friend::equalsIgnoreCase)).toList();
        groupEditor.openAdd(chat, candidates, WIDTH, HEIGHT);
    }

    // новая группа из окна создания: сразу открыть её
    private void groupCreated(String chat, String title) {
        select(chat);
        input.focus();
        warnInfo("Группа «" + title + "» создана");
    }

    // префикс автора в открытой группе: перед ником над его сообщениями
    private String authorPrefix(String author) {
        GroupChats groups = groups();
        if (author == null || partner == null || groups == null || !PrivateMessages.isGroup(partner)) {
            return "";
        }
        return groups.prefixOf(partner, author);
    }

    // клик по аватарке или нику в шапке — профиль собеседника или группы
    private boolean clickHeader(double x, double y) {
        if (partner == null || selection.isActive() || isAnnouncements() || isNotes() || isOfficial()
                || !inside(x, y, CHAT_X + PAD, 0, CHAT_WIDTH - PAD * 2 - 22, HEADER_HEIGHT)) {
            return false;
        }
        input.blur();
        profilePanel.open(partner, PrivateMessages.isGroup(partner) ? ProfilePanel.Tab.MEMBERS : ProfilePanel.Tab.MEDIA, WIDTH, HEIGHT);
        UiSounds.play(UiSound.DROPDOWN_OPEN);
        return true;
    }

    // что просит окно профиля
    private final class ProfileHost implements ProfilePanel.Host {
        @Override
        public void confirm(ConfirmBox.Request request) {
            confirmBox.open(request, WIDTH, HEIGHT);
        }

        @Override
        public void view(MessageImage image) {
            viewer.open(image);
        }

        @Override
        public void invite(String chat) {
            profilePanel.close();
            openGroupEditor(() -> openInvite(chat));
        }

        @Override
        public void write() {
            profilePanel.close();
            input.focus();
        }

        @Override
        public void warn(String text) {
            MessengerScreen.this.warn(text);
        }

        @Override
        public void info(String text) {
            warnInfo(text);
        }

        @Override
        public void jumpTo(String messageKey) {
            profilePanel.close();
            jumpKey = messageKey;
        }
    }

    // что просит окно группы: всё уходит в сеть, ответ — на потоке игры
    private final class GroupHost implements GroupEditor.Host {
        @Override
        public void create(List<String> friends, String title) {
            groups().create(friends, title, chat -> {
                select(chat);
                input.focus();
                warnInfo("Группа «" + title + "» создана");
            }, MessengerScreen.this::warn);
        }

        @Override
        public void add(String chat, String friend) {
            groups().add(chat, friend, MessengerScreen.this::warn);
            warnInfo(friend + " добавлен в группу");
        }

        @Override
        public void kick(String chat, String member) {
            confirmBox.open(new ConfirmBox.Request("Исключить " + member + " из группы?", null, false, "Исключить", true,
                    checked -> groups().kick(chat, member, MessengerScreen.this::warn)), WIDTH, HEIGHT);
        }

        @Override
        public void leave(String chat) {
            confirmBox.open(new ConfirmBox.Request("Выйти из группы «" + messages.displayName(chat)
                    + "»? Переписка у тебя удалится, вернуть может только участник.", null, false, "Выйти", true,
                    checked -> {
                        groups().leave(chat, MessengerScreen.this::warn);
                        if (chat.equals(partner)) {
                            select(null);
                        }
                    }), WIDTH, HEIGHT);
        }

        @Override
        public void rename(String chat, String title) {
            groups().rename(chat, title, MessengerScreen.this::warn);
        }
    }

    // «Выключить звук» у чата, как в Telegram: сообщения приходят, но без звука
    private ChatMenu.Item muteItem(String chat) {
        boolean muted = context.alerts().isMuted(chat);
        return new ChatMenu.Item(muted ? Icon.BELL : Icon.BELL_OFF, muted ? "Включить звук" : "Выключить звук", false,
                () -> context.alerts().toggleMuted(chat));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = toCanvasX(event.x());
        double y = toCanvasY(event.y());
        boolean right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        if (confirmBox.isOpen()) {
            confirmBox.click(x, y);
            UiSounds.play(UiSound.CLICK);
            return true;
        }
        if (viewer.isOpen()) {
            clickViewer(viewer.click(x, y));
            return true;
        }
        if (groupEditor.isOpen()) {
            return afterEditor(groupEditor.click(x, y));
        }
        if (groupCreator.isOpen()) {
            return afterEditor(groupCreator.click(x, y));
        }
        if (profilePanel.isOpen()) {
            return afterEditor(profilePanel.click(x, y));
        }
        if (pollComposer.isOpen()) {
            return afterEditor(pollComposer.click(x, y));
        }
        if (settingsPanel.isOpen()) {
            return afterEditor(settingsPanel.click(x, y));
        }
        if (!right && inside(x, y, (RAIL_WIDTH - RAIL_BUTTON) / 2, settingsButtonY(), RAIL_BUTTON, RAIL_BUTTON)) {
            input.blur();
            settingsPanel.open(WIDTH, HEIGHT);
            UiSounds.play(UiSound.CLICK);
            return true;
        }
        if (forwardPicker.isOpen()) {
            String target = forwardPicker.click(x, y);
            List<PrivateMessage> forwarded = forwardPicker.messages();
            if (target == null || !target.isEmpty()) {
                forwardPicker.close();
            }
            if (target != null && !target.isEmpty()) {
                forwarded.forEach(message -> forwarder.forward(message, chatName(partner), target, chatName(target)));
                selection.clear();
                UiSounds.play(UiSound.SELECT);
            }
            return true;
        }
        if (gifPicker.isOpen()) {
            GifPicker.Click click = gifPicker.click(x, y, right);
            if (click == null) {
                // мимо панели — просто закрыть, клик дальше не идёт
                gifPicker.close();
                return true;
            }
            clickGif(click);
            return true;
        }
        if (!right && attachment.click(x, y)) {
            UiSounds.play(UiSound.CLICK);
            return true;
        }
        if (messageMenu.isOpen() && messageMenu.click(x, y)) {
            UiSounds.play(UiSound.CLICK);
            return true;
        }
        if (menu.click(x, y)) {
            UiSounds.play(UiSound.CLICK);
            return true;
        }
        if (!right && clickHeader(x, y)) {
            return true;
        }
        if (!right && clickChatBars(x, y)) {
            UiSounds.play(UiSound.CLICK);
            return true;
        }
        if (clickBubble(x, y, right)) {
            return true;
        }
        if (!right && clickCard(x, y)) {
            return true;
        }
        if (!right && clickRail(x, y)) {
            return true;
        }
        String entry = entryAt(x, y);
        if (entry != null && isListRow(entry)) {
            if (!right) {
                clickListRow(entry);
            }
            return true;
        }
        if (entry != null) {
            if (right && !ANNOUNCEMENTS.equals(entry)) {
                openMenu(entry, x, y);
                UiSounds.play(UiSound.DROPDOWN_OPEN);
            } else if (!right && !entry.equalsIgnoreCase(partner == null ? "" : partner)) {
                select(entry);
                if (!isReadOnly()) {
                    input.focus();
                } else {
                    input.blur();
                }
                UiSounds.play(UiSound.CLICK);
            }
            return true;
        }
        if (right) {
            return false;
        }
        boolean composing = partner != null && !isAnnouncements() && composer.mouseClicked(x, y, Widget.LEFT_BUTTON);
        return root.mouseClicked(x, y, Widget.LEFT_BUTTON) || composing;
    }

    // картинка — открыть крупно; плашка реакции — поставить или снять свою; правый клик — всплывашка реакций
    private boolean clickBubble(double x, double y, boolean right) {
        int top = messagesTop();
        int bottom = composerTop() - 6;
        if (y < top || y > bottom) {
            return false;
        }
        for (MessageBubbles.Drawn bubble : drawn) {
            // при выделении кликабельна вся строка, а не только пузырь
            boolean onRow = selection.isActive() && y >= bubble.y() && y < bubble.y() + bubble.height() && x >= CHAT_X;
            if (!bubble.contains(x, y) && !onRow) {
                continue;
            }
            PrivateMessage message = bubble.message();
            boolean canReact = canReact(message);
            MessageImage image = bubble.imageAt(x, y);
            if (right) {
                // правый клик по любому сообщению: хотя бы «Переслать» есть всегда
                messageMenu.open(message, actions.items(message, image),
                        canReact ? reaction -> media.react(partner, message, reaction) : null, (float) x, (float) y, WIDTH, HEIGHT);
                UiSounds.play(UiSound.DROPDOWN_OPEN);
                return true;
            }
            if (selection.isActive()) {
                selection.toggle(message);
                UiSounds.play(UiSound.CLICK);
                return true;
            }
            if (bubble.voice() != null && bubble.voice().contains(x, y)) {
                chatMedia.toggleVoice(bubble.voice().voice());
                UiSounds.play(UiSound.CLICK);
                return true;
            }
            PollBlock.Hit option = bubble.pollAt(x, y);
            if (option != null) {
                vote(option);
                return true;
            }
            if (bubble.quoteAt(x, y)) {
                jumpKey = message.reply().id();
                UiSounds.play(UiSound.CLICK);
                return true;
            }
            for (ReactionChips.Hit chip : bubble.chips()) {
                if (chip.contains(x, y) && canReact) {
                    media.react(partner, message, chip.reaction().equals(message.reaction()) ? null : chip.reaction());
                    UiSounds.play(UiSound.CLICK);
                    return true;
                }
            }
            if (image != null) {
                viewer.open(image);
                UiSounds.play(UiSound.CLICK);
                return true;
            }
            return false;
        }
        return false;
    }

    // клик по варианту: другой — голос за него, свой — отозвать. Без сети голосовать нельзя
    private void vote(PollBlock.Hit option) {
        int choice = option.poll().mine() == option.option() ? -1 : option.option();
        UiSounds.play(UiSound.CLICK);
        if (option.message() != null) {
            GroupChats groups = groups();
            if (groups == null) {
                warn("Голос не ушёл: нужна сеть друзей AFriends");
                return;
            }
            groups.vote(partner, option.message(), choice, this::warn);
            return;
        }
        if (!(option.poll() instanceof AnnouncementPoll poll)) {
            return;
        }
        announcements.votes().vote(poll, choice, fresh -> minecraft.execute(() -> {
            if (fresh == null) {
                warn("Голос не ушёл: нужна сеть друзей AFriends");
                return;
            }
            announcements.replacePoll(fresh);
        }));
    }

    // опрос из окна: уходит каждому участнику группы, у себя появится, когда дойдёт
    private void sendPoll(String chat, PollComposer.Poll poll) {
        GroupChats groups = groups();
        if (groups == null) {
            warn("Опросы работают через сеть друзей AFriends — она сейчас не подключена");
            return;
        }
        groups.createPoll(chat, poll.question(), poll.options(), this::warn);
    }

    // куда можно переслать: Избранное, AFriends и переписки, кроме текущей
    private List<String> forwardTargets() {
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

    // как чат называется для людей: в окне пересылки и в пометке «Переслано от …»
    private String chatName(String entry) {
        if (entry == null) {
            return "";
        }
        if (ANNOUNCEMENTS.equals(entry)) {
            return ANNOUNCEMENTS_TITLE;
        }
        if (PrivateMessages.NOTES.equals(entry)) {
            return "Избранное";
        }
        return messages.displayName(entry);
    }

    // реакции — только на сообщения с id и только с другом по сети
    private boolean canReact(PrivateMessage message) {
        return message.id() != null && partner != null && !isReadOnly() && !isNotes() && !isOfficial() && media.available(partner);
    }

    // полосы вокруг ленты: выделение в шапке, плашка закрепа, ответ над полем ввода. true — клик по одной из них
    private boolean clickChatBars(double x, double y) {
        if (partner == null) {
            return false;
        }
        if (selection.isActive() && y < HEADER_HEIGHT && x >= CHAT_X) {
            switch (selection.click(x, y)) {
                case FORWARD -> forwardPicker.open(selection.messages(chatMessages(partner)), forwardTargets(), WIDTH, HEIGHT);
                case DELETE -> actions.delete(selection.messages(chatMessages(partner)));
                case CANCEL -> selection.clear();
                case NONE -> {
                    return false;
                }
            }
            return true;
        }
        switch (pinnedBar.click(x, y)) {
            case PinnedBar.Jump jump -> {
                jumpKey = jump.message().key();
                return true;
            }
            case PinnedBar.Unpin unpin -> {
                context.route().pin(partner, unpin.message(), false, context.route().canSync(partner, List.of(unpin.message())), this::warn);
                return true;
            }
            case PinnedBar.None none -> { }
        }
        return switch (replyStrip.click(x, y)) {
            case JUMP -> {
                jumpKey = replyStrip.reply().id();
                yield true;
            }
            case CANCEL -> true;
            case NONE -> false;
        };
    }

    // что окну переписки дают действия с сообщением
    private final class ActionsHost implements MessageActions.Host {
        @Override
        public String partner() {
            return partner;
        }

        @Override
        public String partnerName() {
            if (isNotes()) {
                return minecraft.getUser().getName();
            }
            return isOfficial() ? PrivateMessages.OFFICIAL_NAME : messages.displayName(partner);
        }

        @Override
        public boolean editable() {
            return partner != null && !isReadOnly();
        }

        @Override
        public void reply(MessageReply reply) {
            replyStrip.start(reply);
            input.focus();
        }

        @Override
        public void forward(List<PrivateMessage> forwarded) {
            forwardPicker.open(forwarded, forwardTargets(), WIDTH, HEIGHT);
        }

        @Override
        public void select(PrivateMessage message) {
            selection.start(message);
        }

        @Override
        public void confirm(ConfirmBox.Request request) {
            confirmBox.open(request, WIDTH, HEIGHT);
        }

        @Override
        public void deleted() {
            selection.clear();
        }
    }

    private void clickGif(GifPicker.Click click) {
        switch (click) {
            case GifPicker.Send send -> {
                gifPicker.close();
                if (partner != null && media.available(partner)) {
                    media.send(partner, send.gif().file(), "");
                }
            }
            case GifPicker.Remove remove -> {
                ui.gifs().remove(remove.gif().sha256());
                warnInfo("Гифка убрана из избранного");
            }
            case GifPicker.Inside inside -> { }
        }
        UiSounds.play(UiSound.CLICK);
    }

    private void clickViewer(ImageViewer.Action action) {
        switch (action) {
            case SAVE -> media.save(viewer.image());
            case OPEN -> media.open(viewer.image());
            case CLOSE -> viewer.close();
            case NONE -> { }
        }
        UiSounds.play(UiSound.CLICK);
    }

    // файлы, перетащенные в окно игры, пока открыта переписка, — отправить картинками
    @Override
    public void onFilesDrop(List<Path> files) {
        if (partner != null && !isReadOnly()) {
            Path file = media.dropped(partner, files);
            if (file != null) {
                attachment.stage(file);
                input.focus();
            }
        }
    }

    private boolean clickCard(double x, double y) {
        for (MessageBubbles.CardButton button : cardButtons) {
            if (button.contains(x, y)) {
                addons.run("messageCard", button.card().action());
                UiSounds.play(UiSound.SELECT);
                return true;
            }
        }
        return false;
    }

    // подпись от дополнения (не ошибка) — тем же местом над полем ввода
    private void warnInfo(String text) {
        warning = text;
        warningAt = System.currentTimeMillis();
    }

    private boolean clickRail(double x, double y) {
        RailAction[] actions = RailAction.values();
        for (int i = 0; i < actions.length; i++) {
            if (!inside(x, y, (RAIL_WIDTH - RAIL_BUTTON) / 2, railButtonY(i), RAIL_BUTTON, RAIL_BUTTON)) {
                continue;
            }
            switch (actions[i]) {
                case READ_ALL -> {
                    messages.markAllRead();
                    UiSounds.play(UiSound.SELECT);
                }
                case ALL_CHATS -> {
                    search.setText("");
                    archiveView = false;
                    if (isNotes()) {
                        select(messages.partners().stream().findFirst().orElse(null));
                    }
                    UiSounds.play(UiSound.CLICK);
                }
                case NOTES -> {
                    select(PrivateMessages.NOTES);
                    input.focus();
                    UiSounds.play(UiSound.CLICK);
                }
                case NEW_GROUP -> openGroupEditor(() -> {
                    input.blur();
                    groupCreator.open(groups(), WIDTH, HEIGHT);
                });
            }
            return true;
        }
        return false;
    }

    private String entryAt(double x, double y) {
        if (x < LIST_X || x >= CHAT_X || y < listTop() || y >= HEIGHT - 4) {
            return null;
        }
        List<String> entries = listEntries();
        int index = (int) Math.floor((y - listTop() + Math.round(listScrollView.get())) / ROW_HEIGHT);
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double x = toCanvasX(mouseX);
        if (gifPicker.scroll(x, toCanvasY(mouseY), scrollY) || forwardPicker.scroll(x, toCanvasY(mouseY), scrollY)
                || groupEditor.scroll(x, toCanvasY(mouseY), scrollY) || groupCreator.scroll(x, toCanvasY(mouseY), scrollY)
                || !viewer.isOpen() && profilePanel.scroll(x, toCanvasY(mouseY), scrollY)) {
            return true;
        }
        int step = (int) Math.round(scrollY * SCROLL_STEP);
        if (x >= LIST_X && x < CHAT_X) {
            listScroll = Math.max(0, listScroll - step);
            listScrollView.setTarget(listScroll);
        } else {
            chatScroll = Math.max(0, chatScroll + step);
            chatScrollView.setTarget(chatScroll);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        boolean enter = key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER;
        if (confirmBox.isOpen()) {
            if (enter) {
                confirmBox.confirm();
            } else if (key == InputConstants.KEY_ESCAPE) {
                confirmBox.close();
            }
            return true;
        }
        if (groupEditor.isOpen()) {
            return afterEditor(groupEditor.keyPressed(key));
        }
        if (groupCreator.isOpen()) {
            return afterEditor(groupCreator.keyPressed(key));
        }
        if (profilePanel.isOpen() && !viewer.isOpen()) {
            return afterEditor(profilePanel.keyPressed(key));
        }
        if (pollComposer.isOpen()) {
            return afterEditor(pollComposer.keyPressed(key));
        }
        if (settingsPanel.isOpen()) {
            return afterEditor(settingsPanel.key(key));
        }
        if (voiceStrip.isRecording()) {
            if (key == InputConstants.KEY_ESCAPE) {
                voiceStrip.cancel();
            } else if (enter) {
                voiceStrip.finish();
            }
            return true;
        }
        if (key == InputConstants.KEY_ESCAPE && (menu.isOpen() || messageMenu.isOpen())) {
            menu.close();
            messageMenu.close();
            return true;
        }
        if (key == InputConstants.KEY_ESCAPE && (viewer.isOpen() || gifPicker.isOpen() || forwardPicker.isOpen())) {
            viewer.close();
            gifPicker.close();
            forwardPicker.close();
            return true;
        }
        // Esc снимает выделение, потом отменяет ответ, и только потом закрывает окно
        if (key == InputConstants.KEY_ESCAPE && selection.isActive()) {
            selection.clear();
            return true;
        }
        if (key == InputConstants.KEY_ESCAPE && replyStrip.isActive()) {
            replyStrip.clear();
            return true;
        }
        if (enter && input.isFocused()) {
            // Shift+Enter — перенос строки, обычный Enter — отправка
            if (isShiftDown()) {
                input.appendNewline();
            } else {
                send();
            }
            return true;
        }
        if (key != InputConstants.KEY_ESCAPE && (root.keyPressed(key) || !isAnnouncements() && composer.keyPressed(key))) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (confirmBox.isOpen()) {
            return true;
        }
        String text = event.codepointAsString();
        if (groupEditor.isOpen()) {
            return groupEditor.charTyped(text);
        }
        if (groupCreator.isOpen()) {
            return groupCreator.charTyped(text);
        }
        if (profilePanel.isOpen()) {
            return profilePanel.charTyped(text);
        }
        if (pollComposer.isOpen()) {
            return pollComposer.charTyped(text);
        }
        if (settingsPanel.isOpen()) {
            return settingsPanel.type(text) || true;
        }
        if (voiceStrip.isRecording()) {
            return true;
        }
        boolean typed = root.charTyped(text) || !isAnnouncements() && composer.charTyped(text);
        if (typed && search.isFocused()) {
            listScroll = 0;
            listScrollView.setTarget(0);
        }
        return typed;
    }

    @Override
    public void removed() {
        voiceStrip.cancel();
        chatMedia.player().stop();
        messages.setDraft(partner, input.getText());
        messages.close();
        MenuBlur.cancel();
        Compat.stopTextInput();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private double toCanvasX(double screenX) {
        return (screenX - offsetX) / scale;
    }

    private double toCanvasY(double screenY) {
        return (screenY - offsetY) / scale;
    }
}
