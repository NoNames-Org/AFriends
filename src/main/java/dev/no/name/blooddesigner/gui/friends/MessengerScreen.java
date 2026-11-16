package dev.no.name.blooddesigner.gui.friends;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import dev.no.name.blooddesigner.core.modes.BloodstoneServer;
import dev.no.name.blooddesigner.net.announcements.AnnouncementFeed;
import dev.no.name.blooddesigner.addon.Addons;
import dev.no.name.blooddesigner.api.ComposeAction;
import dev.no.name.blooddesigner.api.MessageCard;
import dev.no.name.blooddesigner.compat.Compat;
import dev.no.name.blooddesigner.compat.Gfx;
import dev.no.name.blooddesigner.compat.ScreenBase;
import dev.no.name.blooddesigner.core.Friend;
import dev.no.name.blooddesigner.core.FriendManager;
import dev.no.name.blooddesigner.core.IgnoreList;
import dev.no.name.blooddesigner.core.MessageRoute;
import dev.no.name.blooddesigner.core.PrivateMessage;
import dev.no.name.blooddesigner.core.PrivateMessages;
import dev.no.name.blooddesigner.core.party.PartyService;
import dev.no.name.blooddesigner.gui.Theme;
import dev.no.name.blooddesigner.gui.animation.Animated;
import dev.no.name.blooddesigner.gui.animation.Easing;
import dev.no.name.blooddesigner.gui.animation.Motion;
import dev.no.name.blooddesigner.gui.render.Colors;
import dev.no.name.blooddesigner.gui.render.Draw;
import dev.no.name.blooddesigner.gui.render.blur.MenuBlur;
import dev.no.name.blooddesigner.gui.render.icon.Icon;
import dev.no.name.blooddesigner.gui.render.text.TextStyle;
import dev.no.name.blooddesigner.gui.sound.UiSound;
import dev.no.name.blooddesigner.gui.sound.UiSounds;
import dev.no.name.blooddesigner.gui.widget.ButtonWidget;
import dev.no.name.blooddesigner.gui.widget.IconButtonWidget;
import dev.no.name.blooddesigner.gui.widget.ContainerWidget;
import dev.no.name.blooddesigner.gui.widget.TextFieldWidget;
import dev.no.name.blooddesigner.gui.widget.Widget;
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
    private static final int SEND_WIDTH = 26;
    // полоса ввода как в Telegram: скрепка слева, поле, значки справа; «отправить» — рядом
    private static final int BAR_X = CHAT_X + PAD;
    private static final int BAR_WIDTH = CHAT_WIDTH - PAD * 2 - SEND_WIDTH - 4;
    private static final int BAR_ICON = 16;
    private static final int SEPARATOR_HEIGHT = 18;
    private static final int SCROLL_STEP = 16;
    private static final int MAX_TEXT = 200;
    private static final int DANGER = 0xFFFF6B6B;
    private static final int NOTES_COLOR = 0xFF3D7BFF;
    private static final int ANNOUNCEMENTS_COLOR = 0xFFE0313F;
    /** Чат BloodDesigner: переписка с админами мода через портал. Не игнорируется и не закрепляется. */
    private static final int OFFICIAL_COLOR = 0xFFB3122A;
    /** Чат «Объявления»: всегда первый, только для чтения, не очищается и не удаляется. Пишут с portal.no-names.com. */
    private static final String ANNOUNCEMENTS = "$announcements";
    private static final String ANNOUNCEMENTS_TITLE = "Объявления";
    private static final long ANNOUNCEMENTS_SINCE = java.time.LocalDate.of(2026, 11, 16)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    /** Пока с сервера ничего не пришло. */
    private static final List<PrivateMessage> ANNOUNCEMENTS_EMPTY = List.of(new PrivateMessage(false,
            "Здесь будут объявления BloodDesigner: новости мода и важные изменения. Писать сюда нельзя.",
            ANNOUNCEMENTS_SINCE));
    private static final long WARNING_MS = 4000;

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

    public record Context(FriendManager friends, PrivateMessages messages, IgnoreList ignored, PartyService party,
                          MessageRoute route, Runnable save) {
    }

    private record Pending(String partner, String text, long sentAt) {
    }

    private record Row(String day, MessageBubbles.Bubble bubble, int index, float height) {
    }

    private enum RailAction { READ_ALL, ALL_CHATS, NOTES }

    private final Context context;
    private final PrivateMessages messages;
    private final List<Pending> pending = new ArrayList<>();

    private final Map<Integer, Long> appearAt = new HashMap<>();
    private final ContainerWidget root = new ContainerWidget();
    private final ContainerWidget composer = new ContainerWidget();
    private final TextFieldWidget search = new TextFieldWidget(Icon.SEARCH, "Поиск").withMaxLength(16);
    private final TextFieldWidget input;
    private final ChatMenu menu = new ChatMenu();
    // кнопки карточек дополнений, нарисованные в этом кадре
    private final List<MessageBubbles.CardButton> cardButtons = new ArrayList<>();
    // где нарисованы пузыри на этом кадре: клик по картинке, реакциям, правый клик по сообщению
    private final List<MessageBubbles.Drawn> drawn = new ArrayList<>();
    private final ReactionBar reactionBar = new ReactionBar();
    private final ImageViewer viewer = new ImageViewer();
    private final MediaActions media;
    private final AttachmentStrip attachment = new AttachmentStrip();
    private final GifPicker gifPicker = new GifPicker();
    private final ForwardPicker forwardPicker = new ForwardPicker();
    private final Forwarder forwarder;
    // почему сообщение не ушло: подпись над полем ввода
    private String warning = "";
    private long warningAt;
    private final Animated windowOpen = new Animated(0, Motion.SLOW);
    private final Animated chatScrollView = new Animated(0, Motion.FAST);
    private final Animated listScrollView = new Animated(0, Motion.FAST);

    private String partner;
    private int chatScroll;
    private int listScroll;
    private int shownCount;

    private int pixelScale = 1;
    private float scale = 1;
    private float offsetX;
    private float offsetY;

    public MessengerScreen(String partner, Context context) {
        super(Component.literal("Сообщения"));
        this.context = context;
        this.messages = context.messages();
        this.media = new MediaActions(context.route(), this::warn, this::warnInfo);
        this.forwarder = new Forwarder(context.route(), this::warn, this::warnInfo);
        AnnouncementFeed.get().refresh();
        input = new TextFieldWidget(null, "Написать сообщение…").withMaxLength(MAX_TEXT).withoutFrame();
        int barY = HEIGHT - PAD - INPUT_HEIGHT;
        // скрепка — слева внутри полосы: выбранная картинка встаёт над полем и уйдёт вместе с текстом
        IconButtonWidget attach = new IconButtonWidget(Icon.IMAGE, () -> media.choose(this.partner, file -> {
            attachment.stage(file);
            input.focus();
        }));
        attach.setBounds(BAR_X + 2, barY, BAR_ICON, INPUT_HEIGHT);
        composer.addChild(attach);
        // справа внутри полосы: кнопки дополнений и избранные гифки
        List<ComposeAction> actions = Addons.get().composeActions();
        int icons = actions.size() + 1;
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
        input.setBounds(BAR_X + 2 + BAR_ICON + 2, barY, iconsX - (BAR_X + 2 + BAR_ICON + 2) - 2, INPUT_HEIGHT);
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
        reactionBar.close();
        forwardPicker.close();
        viewer.close();
        gifPicker.close();
        attachment.clear();
        messages.close();
        partner = chat;
        pending.clear();
        appearAt.clear();
        chatScroll = 0;
        chatScrollView.snap(0);
        if (chat != null) {
            if (!ANNOUNCEMENTS.equals(chat)) {
                messages.open(chat);
            }
            shownCount = chatMessages(chat).size();
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

    private static List<PrivateMessage> announcementList() {
        List<PrivateMessage> loaded = AnnouncementFeed.get().messages();
        return loaded.isEmpty() ? ANNOUNCEMENTS_EMPTY : loaded;
    }

    private boolean isNotes() {
        return PrivateMessages.NOTES.equals(partner);
    }

    private List<String> listEntries() {
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        if (query.isEmpty() || "объявления".contains(query)) {
            result.add(ANNOUNCEMENTS);
        }
        if (query.isEmpty() || "избранное".contains(query)) {
            result.add(PrivateMessages.NOTES);
        }
        for (String name : messages.partners()) {
            if (query.isEmpty() || messages.displayName(name).toLowerCase(Locale.ROOT).contains(query)) {
                result.add(name);
            }
        }
        return result;
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
        int hoverX = menu.isOpen() ? -1000 : canvasX;

        float shown = windowOpen.get();
        Draw.pushAlpha(shown);
        Draw.pushOffset(g, 0, (1 - shown) * Motion.RISE);
        Draw.shadow(g, 0, 4, WIDTH, HEIGHT, Theme.PANEL_RADIUS, 24, Theme.SHADOW);
        Draw.gradient(g, 0, 0, WIDTH, HEIGHT, Theme.PANEL_RADIUS, Theme.PANEL_TOP, Theme.PANEL_BOTTOM);
        Draw.border(g, 0, 0, WIDTH, HEIGHT, Theme.PANEL_RADIUS, Theme.BORDER);

        Draw.rect(g, LIST_X, 1, 1, HEIGHT - 2, Theme.BORDER);
        Draw.rect(g, CHAT_X, 1, 1, HEIGHT - 2, Theme.BORDER);

        // пока окно открыто, объявления подтягиваются сами (запрос не чаще раза в 15 секунд)
        AnnouncementFeed.get().refresh();
        drawRail(g, hoverX, canvasY);
        drawList(g, hoverX, canvasY);
        drawChat(g);
        root.render(g, hoverX, canvasY);
        if (isAnnouncements()) {
            drawReadOnly(g, "Только чтение — сюда приходят новости мода");
        } else if (partner != null) {
            drawBar(g, hoverX, canvasY);
            composer.render(g, hoverX, canvasY);
        }
        g.nextStratum();
        drawRailTooltip(g, hoverX, canvasY);
        menu.draw(g, canvasX, canvasY);
        reactionBar.draw(g, canvasX, canvasY);
        gifPicker.draw(g, canvasX, canvasY);
        forwardPicker.draw(g, canvasX, canvasY, this::chatName, this::drawEntryTile);
        if (viewer.isOpen()) {
            g.nextStratum();
            viewer.draw(g, context.route(), WIDTH, HEIGHT, canvasX, canvasY);
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
    }

    private static Icon railIcon(RailAction action) {
        return switch (action) {
            case READ_ALL -> Icon.CHECK_SQUARE;
            case ALL_CHATS -> Icon.MESSAGE;
            case NOTES -> Icon.HEART;
        };
    }

    private static String railLabel(RailAction action) {
        return switch (action) {
            case READ_ALL -> "Прочитать всё";
            case ALL_CHATS -> "Все чаты";
            case NOTES -> "Избранное";
        };
    }

    private void drawRailTooltip(Gfx g, int mouseX, int mouseY) {
        RailAction[] actions = RailAction.values();
        for (int i = 0; i < actions.length; i++) {
            int y = railButtonY(i);
            if (inside(mouseX, mouseY, (RAIL_WIDTH - RAIL_BUTTON) / 2, y, RAIL_BUTTON, RAIL_BUTTON)) {
                String label = railLabel(actions[i]);
                float width = Draw.width(label) + 12;
                Draw.shadow(g, RAIL_WIDTH + 2, y + 4, width, 16, Theme.RADIUS, 8, Theme.SHADOW);
                Draw.box(g, RAIL_WIDTH + 2, y + 4, width, 16, Theme.RADIUS, Theme.POPUP, Theme.BORDER_HOVER);
                Draw.text(g, label, RAIL_WIDTH + 8, y + 8, Theme.TEXT);
            }
        }
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
        float pinSpace = pinned ? Theme.ICON_SMALL + 3 : 0;
        int nameWidth = (int) (x + width - 6 - textX - dateWidth - pinSpace - 4);
        String title = announcements ? ANNOUNCEMENTS_TITLE : notes ? "Избранное" : messages.displayName(entry);
        Draw.text(g, Draw.trim(title, nameWidth), textX, rowY + 6, Theme.TEXT, TextStyle.BODY_MEDIUM);
        Draw.text(g, date, x + width - 6 - dateWidth, rowY + 7, Theme.TEXT_DIM, TextStyle.SMALL);
        if (pinned) {
            Draw.icon(g, Icon.PIN, x + width - 6 - dateWidth - pinSpace, rowY + 6, Theme.ICON_SMALL - 1, Theme.TEXT_DIM);
        }

        int unread = notes ? 0 : messages.unread(entry);
        float badgeWidth = unread > 0 ? Draw.width(String.valueOf(unread), TextStyle.SMALL) + 8 : 0;
        String preview = last == null ? (notes ? "Твой личный чат для заметок" : "Пусто")
                : (last.fromMe() && !notes ? "Ты: " : "") + previewText(entry, last);
        int previewWidth = (int) (x + width - 6 - textX - badgeWidth - (badgeWidth > 0 ? 4 : 0));
        Draw.text(g, Draw.trim(preview, previewWidth), textX, rowY + 17, Theme.TEXT_SOFT, TextStyle.SMALL);
        if (unread > 0) {
            float badgeX = x + width - 6 - badgeWidth;
            Draw.roundedRect(g, badgeX, rowY + 16, badgeWidth, 10, 5, Theme.ACCENT);
            Draw.text(g, String.valueOf(unread), badgeX + 4, rowY + 18, Theme.ACCENT_TEXT, TextStyle.SMALL);
        }
    }

    // текст в списке чатов: скрыт для стримера, у карточки дополнения — её заголовок
    private static String previewText(String entry, PrivateMessage last) {
        Addons addons = Addons.get();
        if (addons.hideMessages()) {
            return "Скрыто";
        }
        if (!last.images().isEmpty()) {
            return dev.no.name.blooddesigner.core.MessageContent.preview(last);
        }
        MessageCard.Card card = addons.card(entry, last.text(), last.fromMe());
        return card == null ? last.text() : card.title();
    }

    private static void drawAnnouncementsTile(Gfx g, float x, float y, int size) {
        Draw.roundedRect(g, x, y, size, size, Theme.RADIUS, ANNOUNCEMENTS_COLOR);
        float icon = size * 0.55f;
        Draw.icon(g, Icon.BELL, x + (size - icon) / 2, y + (size - icon) / 2, icon, 0xFFFFFFFF);
    }

    // значок чата: объявления, BloodDesigner, избранное или голова игрока (список чатов и окно пересылки)
    private void drawEntryTile(Gfx g, String entry, float x, float y, int size) {
        if (ANNOUNCEMENTS.equals(entry)) {
            drawAnnouncementsTile(g, x, y, size);
        } else if (PrivateMessages.isOfficial(entry)) {
            drawOfficialTile(g, x, y, size);
        } else if (PrivateMessages.NOTES.equals(entry)) {
            drawNotesTile(g, x, y, size);
        } else {
            drawFace(g, context.friends().skinOf(entry), (int) x, (int) y, size);
            if (OnlineDot.isOnline(entry)) {
                OnlineDot.draw(g, x, y, size);
            }
        }
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
        drawMessages(g);
        drawWarning(g);
    }

    // верх области ввода: если картинка прикреплена, её превью стоит над полосой
    private int composerTop() {
        return HEIGHT - PAD - INPUT_HEIGHT - (attachment.isStaged() && !isReadOnly() ? AttachmentStrip.HEIGHT + 4 : 0);
    }

    // общая рамка полосы ввода: внутри неё скрепка, поле и значки
    private void drawBar(Gfx g, int mouseX, int mouseY) {
        int barY = HEIGHT - PAD - INPUT_HEIGHT;
        int border = input.isFocused() ? Theme.BORDER_SELECTED : Theme.BORDER;
        Draw.box(g, BAR_X, barY, BAR_WIDTH, INPUT_HEIGHT, Theme.RADIUS, Theme.CARD, border);
        attachment.draw(g, BAR_X, barY - AttachmentStrip.HEIGHT - 4, BAR_WIDTH + SEND_WIDTH + 4, mouseX, mouseY);
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
        int faceX = CHAT_X + PAD + 2;
        int faceY = (HEADER_HEIGHT - HEADER_FACE) / 2;
        String title;
        String status;
        int statusColor = Theme.TEXT_SOFT;
        if (isAnnouncements()) {
            drawAnnouncementsTile(g, faceX, faceY, HEADER_FACE);
            title = ANNOUNCEMENTS_TITLE;
            status = "Новости BloodDesigner · только чтение";
        } else if (isOfficial()) {
            drawOfficialTile(g, faceX, faceY, HEADER_FACE);
            title = PrivateMessages.OFFICIAL_NAME;
            status = "Администрация мода · ответ видят только админы";
        } else if (isNotes()) {
            drawNotesTile(g, faceX, faceY, HEADER_FACE);
            title = "Избранное";
            status = "Твой личный чат для заметок";
        } else {
            drawFace(g, context.friends().skinOf(partner), faceX, faceY, HEADER_FACE);
            if (OnlineDot.isOnline(partner)) {
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
        int top = HEADER_HEIGHT + 4;
        int bottom = composerTop() - 6;
        List<PrivateMessage> chat = chatMessages(partner);
        trackNewMessages(chat);
        long now = System.currentTimeMillis();
        pending.removeIf(item -> !isFailed(item) && now - item.sentAt() > PENDING_TIMEOUT_MS);
        List<Pending> mine = pending.stream().filter(item -> item.partner().equalsIgnoreCase(partner)).toList();

        List<PrivateMessage> shown = new ArrayList<>(chat);
        mine.forEach(item -> shown.add(new PrivateMessage(true, item.text(), item.sentAt())));
        if (shown.isEmpty()) {
            float centerX = CHAT_X + CHAT_WIDTH / 2f;
            String text = isNotes() ? "Пиши сюда заметки — их видишь только ты" : "Сообщений пока нет — напиши первым";
            Draw.textCentered(g, text, centerX, (top + bottom) / 2f, Theme.TEXT_SOFT);
            return;
        }
        float left = CHAT_X + PAD;
        float right = WIDTH - PAD;
        List<Row> rows = buildRows(shown, right - left, chat.size(), partner);
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

        g.enableScissor(CHAT_X + 1, top, WIDTH - 1, bottom);
        float y = bottom - content + Math.round(chatScrollView.get());
        for (Row row : rows) {
            if (y + row.height() >= top && y <= bottom) {
                if (row.day() != null) {
                    drawDay(g, row.day(), y);
                } else {
                    boolean isPending = row.index() >= chat.size();
                    Pending item = isPending ? mine.get(row.index() - chat.size()) : null;
                    long start = isPending ? item.sentAt() : appearAt.getOrDefault(row.index(), 0L);
                    MessageBubbles.Stamp stamp = !isPending ? MessageBubbles.Stamp.TIME
                            : isFailed(item) ? MessageBubbles.Stamp.FAILED : MessageBubbles.Stamp.PENDING;
                    MessageBubbles.Drawn bubble = drawAnimated(g, row.bubble(), left, right, y, start, stamp, now, context.route());
                    if (bubble.card() != null && y >= top && y + row.height() <= bottom) {
                        cardButtons.add(bubble.card());
                    }
                    if (!isPending) {
                        drawn.add(bubble);
                    }
                }
            }
            y += row.height();
        }
        g.disableScissor();
    }

    private static List<Row> buildRows(List<PrivateMessage> shown, float areaWidth, int firstPending, String partner) {
        List<MessageBubbles.Bubble> bubbles = MessageBubbles.layout(shown, areaWidth, firstPending, partner);
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
        if (chatScroll < SCROLL_STEP) {
            chatScroll = 0;
            chatScrollView.setTarget(0);
        }
        shownCount = chat.size();
    }

    private static MessageBubbles.Drawn drawAnimated(Gfx g, MessageBubbles.Bubble bubble, float left, float right,
                                                     float y, long start, MessageBubbles.Stamp stamp, long now,
                                                     MessageRoute route) {
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
        MessageBubbles.Drawn drawnBubble = MessageBubbles.draw(g, bubble, left, right, y, stamp, route);
        Draw.popOffset(g);
        Draw.popAlpha();
        return drawnBubble;
    }

    private void send() {
        String text = input.getText().strip();
        // прикреплённая картинка уходит вместе с текстом: картинка сверху, текст подписью
        if (attachment.isStaged()) {
            if (partner != null && !isReadOnly() && media.send(partner, attachment.file(), text)) {
                attachment.clear();
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
        if (isNotes()) {
            messages.addNote(text, System.currentTimeMillis());
        } else {
            // другу по сети — сквозным шифрованием через сервер сети, остальным — /msg
            Optional<String> problem = context.route().send(partner, text, this::warn);
            if (problem.isPresent()) {
                warn(problem.get());
                return false;
            }

            pending.add(new Pending(partner, text, System.currentTimeMillis()));
        }
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
        Addons addons = Addons.get();
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
        // BloodDesigner нельзя игнорировать, закрепить или удалить из друзей — только почистить у себя
        if (PrivateMessages.isOfficial(entry)) {
            items.add(new ChatMenu.Item(Icon.BRUSH, "Очистить чат", true, () -> {
                messages.clear(entry);
                if (isOfficial()) {
                    select(entry);
                }
            }));
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
        String name = messages.displayName(entry);
        FriendManager friends = context.friends();
        IgnoreList ignored = context.ignored();
        boolean friend = friends.find(name) != null;
        items.add(new ChatMenu.Item(friend ? Icon.USER_OFF : Icon.USERS, friend ? "Удалить из друзей" : "Добавить в друзья",
                friend, () -> {
                    if (friend) {
                        friends.remove(name);
                        Addons.get().removed(name);
                    } else if (friends.add(name) == FriendManager.AddResult.ADDED) {
                        Addons.get().added(name, this::warnInfo);
                    }
                    context.save().run();
                }));
        boolean isIgnored = ignored.isIgnored(name);
        items.add(new ChatMenu.Item(Icon.BAN, isIgnored ? "Не игнорировать" : "Игнорировать", false, () -> {
            ignored.toggle(name);
            context.save().run();
        }));
        // в пати зовём только того, кто на этом же сервере, и только на bloodstone, где есть /party
        if (BloodstoneServer.isConnected() && Addons.get().reachable(name)) {
            items.add(new ChatMenu.Item(Icon.FLAG, "Пригласить в пати", false, () -> context.party().invite(name)));
        }
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

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = toCanvasX(event.x());
        double y = toCanvasY(event.y());
        boolean right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        if (viewer.isOpen()) {
            clickViewer(viewer.click(x, y));
            return true;
        }
        if (forwardPicker.isOpen()) {
            String target = forwardPicker.click(x, y);
            PrivateMessage forwarded = forwardPicker.message();
            if (target == null || !target.isEmpty()) {
                forwardPicker.close();
            }
            if (target != null && !target.isEmpty()) {
                forwarder.forward(forwarded, chatName(partner), target, chatName(target));
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
        if (reactionBar.isOpen()) {
            PrivateMessage target = reactionBar.message();
            dev.no.name.blooddesigner.core.MessageImage targetImage = reactionBar.target();
            ReactionBar.Choice choice = reactionBar.click(x, y);
            reactionBar.close();
            if (choice != null) {
                applyChoice(target, targetImage, choice);
                return true;
            }
        }
        if (menu.click(x, y)) {
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
        int top = HEADER_HEIGHT + 4;
        int bottom = composerTop() - 6;
        if (y < top || y > bottom) {
            return false;
        }
        for (MessageBubbles.Drawn bubble : drawn) {
            if (!bubble.contains(x, y)) {
                continue;
            }
            PrivateMessage message = bubble.message();
            boolean canReact = canReact(message);
            dev.no.name.blooddesigner.core.MessageImage image = bubble.imageAt(x, y);
            if (right) {
                // правый клик по любому сообщению: хотя бы «Переслать» есть всегда
                reactionBar.open(message, image, canReact, (float) x, (float) y, WIDTH, HEIGHT);
                UiSounds.play(UiSound.DROPDOWN_OPEN);
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

    // куда можно переслать: Избранное, BloodDesigner и переписки, кроме текущей
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

    private void applyChoice(PrivateMessage message, dev.no.name.blooddesigner.core.MessageImage image, ReactionBar.Choice choice) {
        if (choice.kind() == null) {
            return;
        }
        switch (choice.kind()) {
            case REACT -> media.react(partner, message, choice.reaction());
            case SAVE -> media.save(image);
            case OPEN -> media.open(image);
            case FAVORITE -> media.toggleFavorite(image);
            case FORWARD -> forwardPicker.open(message, forwardTargets(), WIDTH, HEIGHT);
        }
        UiSounds.play(UiSound.CLICK);
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
                dev.no.name.blooddesigner.gui.media.GifFavorites.get().remove(remove.gif().sha256());
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
                Addons.get().run("messageCard", button.card().action());
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
        if (gifPicker.scroll(x, toCanvasY(mouseY), scrollY) || forwardPicker.scroll(x, toCanvasY(mouseY), scrollY)) {
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
        if (key == InputConstants.KEY_ESCAPE && menu.isOpen()) {
            menu.close();
            return true;
        }
        if (key == InputConstants.KEY_ESCAPE && (viewer.isOpen() || reactionBar.isOpen() || gifPicker.isOpen() || forwardPicker.isOpen())) {
            viewer.close();
            reactionBar.close();
            gifPicker.close();
            forwardPicker.close();
            return true;
        }
        if ((key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) && input.isFocused()) {
            send();
            return true;
        }
        if (key != InputConstants.KEY_ESCAPE && (root.keyPressed(key) || !isAnnouncements() && composer.keyPressed(key))) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        String text = event.codepointAsString();
        boolean typed = root.charTyped(text) || !isAnnouncements() && composer.charTyped(text);
        if (typed && search.isFocused()) {
            listScroll = 0;
            listScrollView.setTarget(0);
        }
        return typed;
    }

    @Override
    public void removed() {
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
