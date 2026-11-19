package dev.no.name.afriends.gui.friends;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import dev.no.name.afriends.api.ComposeAction;
import dev.no.name.afriends.compat.Compat;
import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.compat.ScreenBase;
import dev.no.name.afriends.core.FriendManager;
import dev.no.name.afriends.core.IgnoreList;
import dev.no.name.afriends.core.MessageRoute;
import dev.no.name.afriends.core.NotifySettings;
import dev.no.name.afriends.core.PrivateMessages;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.UiServices;
import dev.no.name.afriends.gui.animation.Animated;
import dev.no.name.afriends.gui.animation.Motion;
import dev.no.name.afriends.gui.media.ChatMedia;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.blur.MenuBlur;
import dev.no.name.afriends.gui.render.icon.Icon;
import dev.no.name.afriends.gui.sound.UiSound;
import dev.no.name.afriends.gui.sound.UiSounds;
import dev.no.name.afriends.gui.widget.ButtonWidget;
import dev.no.name.afriends.gui.widget.ContainerWidget;
import dev.no.name.afriends.gui.widget.Widget;
import dev.no.name.afriends.net.announcements.AnnouncementFeed;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

import java.nio.file.Path;
import java.util.List;

import static dev.no.name.afriends.gui.friends.MessengerLayout.CHAT_X;
import static dev.no.name.afriends.gui.friends.MessengerLayout.HEADER_HEIGHT;
import static dev.no.name.afriends.gui.friends.MessengerLayout.HEIGHT;
import static dev.no.name.afriends.gui.friends.MessengerLayout.LIST_X;
import static dev.no.name.afriends.gui.friends.MessengerLayout.PAD;
import static dev.no.name.afriends.gui.friends.MessengerLayout.SCREEN_MARGIN;
import static dev.no.name.afriends.gui.friends.MessengerLayout.SCROLL_STEP;
import static dev.no.name.afriends.gui.friends.MessengerLayout.WIDTH;

/**
 * Окно мессенджера: масштаб холста и анимация открытия, порядок отрисовки частей и раздача событий.
 * Сначала событие получают всплывающие окна ({@link OverlayStack}), потом шапка, лента, рейка, список и поле ввода
 */
public class MessengerScreen extends ScreenBase implements ChatNavigator {
    public record Context(FriendManager friends, PrivateMessages messages, IgnoreList ignored,
                          MessageRoute route, Runnable save, NotifySettings alerts, UiServices ui) {
    }

    private final PrivateMessages messages;
    private final ChatController controller;
    private final AnnouncementFeed announcements;
    private final ChatMedia chatMedia;
    private final Selection selection = new Selection();
    private final ComposerView composer;
    private final MessengerPopups popups;
    private final ChatMenus menus;
    private final ChatTiles tiles;
    private final ChatListView list;
    private final ChatHeaderView header;
    private final MessageFeedView feed;
    private final RailView rail = new RailView();
    private final OverlayStack overlays;
    // поле поиска и крестик закрытия
    private final ContainerWidget root = new ContainerWidget();
    private final Animated windowOpen = new Animated(0, Motion.SLOW);
    private int pixelScale = 1;
    private float scale = 1;
    private float offsetX;
    private float offsetY;

    public MessengerScreen(String partner, Context context) {
        super(Component.literal("Сообщения"));
        this.messages = context.messages();
        UiServices ui = context.ui();
        this.announcements = ui.announcements();
        this.chatMedia = ui.media(context.route());
        this.controller = new ChatController(messages, context.route(), context.alerts(), announcements::messages,
                new ScreenFeedback(), System::currentTimeMillis);
        MediaActions media = new MediaActions(context.route(), ui.gifs(), controller::warn, controller::info);
        this.composer = new ComposerView(controller, media, new AttachmentStrip(ui.images()), ui.addons().composeActions(),
                new ComposerHost());
        this.popups = new MessengerPopups(context, controller, this, chatMedia, media, composer, selection);
        this.menus = new ChatMenus(context, controller, this, popups, composer::sendText);
        this.tiles = new ChatTiles(context.friends(), messages, ui.addons(), chatMedia);
        this.list = new ChatListView(controller, messages, context.alerts(), ui.addons(), context.route(), tiles, this, menus);
        this.header = new ChatHeaderView(controller, messages, context.friends(), context.route(), tiles, selection);
        BubbleContext bubbles = new BubbleContext(chatMedia, ui.addons(), announcements, messages::receipt,
                controller::authorPrefix);
        this.feed = new MessageFeedView(controller, bubbles, chatMedia, ui.addons(), announcements, context.route(),
                selection, composer, popups, task -> minecraft.execute(task));
        this.overlays = MessengerOverlays.build(popups, composer, selection, rail);
        announcements.refresh();
        ButtonWidget close = new ButtonWidget(Icon.CLOSE, this::onClose);
        close.setBounds(WIDTH - PAD - 16, (HEADER_HEIGHT - 16) / 2, 16, 16);
        root.setBounds(0, 0, WIDTH, HEIGHT);
        root.addChild(list.search());
        root.addChild(close);
        select(partner != null ? partner : messages.partners().stream().findFirst().orElse(null));
        windowOpen.setTarget(1);
        UiSounds.play(UiSound.MENU_OPEN);
    }

    // звуки предупреждения и отправки: контроллер о звуках не знает
    private static final class ScreenFeedback implements ChatController.Feedback {
        @Override
        public void warned() {
            UiSounds.play(UiSound.CLICK);
        }

        @Override
        public void sent() {
            UiSounds.play(UiSound.CLICK);
        }
    }

    // кнопки полосы ввода, которым нужны меню и лента
    private final class ComposerHost implements ComposerView.Host {
        @Override
        public void compose(ComposeAction action, int buttonX) {
            menus.openCompose(action, buttonX);
        }

        @Override
        public void toggleGifs() {
            popups.toggleGifs(composer.top());
        }

        @Override
        public void sent() {
            feed.scrollToBottom();
        }
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
        if (controller.partner() != null && !controller.isReadOnly()) {
            composer.input().focus();
        }
    }

    // ---- ChatNavigator ----

    @Override
    public void select(String chat) {
        popups.closeForChat();
        selection.clear();
        feed.reset();
        composer.resetForChat();
        // черновик уходящего чата сохраняется, у нового — подставляется
        composer.input().setText(controller.select(chat, composer.input().getText()));
    }

    @Override
    public void focusInput() {
        composer.input().focus();
    }

    @Override
    public void blurInput() {
        composer.input().blur();
    }

    @Override
    public void refocusInput() {
        composer.input().refocus();
    }

    // ---- отрисовка ----

    @Override
    protected void drawBackground(Gfx g, int mouseX, int mouseY, float partialTick) {
        float shown = windowOpen.get();
        MenuBlur.request(shown);
        g.blurBeforeThisStratum();
        Draw.pushAlpha(shown);
        Draw.backdrop(g, width, height, Theme.dim(), Theme.DIM_VIGNETTE, Theme.DIM_GRAIN);
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
        int hoverX = popups.blocksHover() ? -1000 : canvasX;

        float shown = windowOpen.get();
        Draw.pushAlpha(shown);
        Draw.pushOffset(g, 0, (1 - shown) * Motion.RISE);
        drawPanel(g);
        // пока окно открыто, объявления подтягиваются сами (запрос не чаще раза в 15 секунд)
        announcements.refresh();
        composer.updateLayout();
        rail.draw(g, hoverX, canvasY, controller.isNotes(), popups.settingsPanel().isOpen());
        list.draw(g, hoverX, canvasY);
        drawChat(g, canvasX, canvasY, hoverX);
        root.render(g, hoverX, canvasY);
        composer.draw(g, hoverX, canvasY);
        g.nextStratum();
        rail.drawTooltip(g, hoverX, canvasY);
        popups.draw(g, canvasX, canvasY, tiles);
        Draw.popOffset(g);
        Draw.popAlpha();
        pose.popMatrix();
    }

    private static void drawPanel(Gfx g) {
        Draw.shadow(g, 0, 4, WIDTH, HEIGHT, Theme.PANEL_RADIUS, 24, Theme.shadow());
        Draw.gradient(g, 0, 0, WIDTH, HEIGHT, Theme.PANEL_RADIUS, Theme.panelTop(), Theme.panelBottom());
        Draw.border(g, 0, 0, WIDTH, HEIGHT, Theme.PANEL_RADIUS, Theme.border());
        Draw.rect(g, LIST_X, 1, 1, HEIGHT - 2, Theme.border());
        Draw.rect(g, CHAT_X, 1, 1, HEIGHT - 2, Theme.border());
    }

    // шапка видит курсор всегда, закреп — только когда поверх нет меню
    private void drawChat(Gfx g, int mouseX, int mouseY, int hoverX) {
        if (controller.partner() == null) {
            MessageFeedView.drawNoChat(g);
            return;
        }
        header.draw(g, mouseX, mouseY);
        feed.drawPinnedBar(g, hoverX, mouseY);
        feed.drawMessages(g);
        composer.drawWarning(g);
    }

    // ---- события ----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = toCanvasX(event.x());
        double y = toCanvasY(event.y());
        boolean right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        Overlay.Result overlay = overlays.click(x, y, right);
        if (overlay != Overlay.Result.PASS) {
            return overlay == Overlay.Result.CONSUMED;
        }
        if (!right && header.opensProfile(x, y)) {
            popups.openProfile(controller.partner());
            return true;
        }
        if (!right && feed.clickBars(x, y)) {
            UiSounds.play(UiSound.CLICK);
            return true;
        }
        if (feed.clickBubble(x, y, right) || !right && (feed.clickCard(x, y) || clickRail(x, y))) {
            return true;
        }
        if (list.click(x, y, right)) {
            return true;
        }
        if (right) {
            return false;
        }
        boolean composing = composer.mouseClicked(x, y);
        return root.mouseClicked(x, y, Widget.LEFT_BUTTON) || composing;
    }

    private boolean clickRail(double x, double y) {
        RailView.Action action = rail.actionAt(x, y);
        if (action == null) {
            return false;
        }
        switch (action) {
            case READ_ALL -> {
                messages.markAllRead();
                UiSounds.play(UiSound.SELECT);
            }
            case ALL_CHATS -> {
                list.search().setText("");
                controller.setArchiveView(false);
                if (controller.isNotes()) {
                    select(messages.partners().stream().findFirst().orElse(null));
                }
                UiSounds.play(UiSound.CLICK);
            }
            case NOTES -> {
                select(PrivateMessages.NOTES);
                composer.input().focus();
                UiSounds.play(UiSound.CLICK);
            }
            case NEW_GROUP -> popups.openNewGroup();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double x = toCanvasX(mouseX);
        if (overlays.scroll(x, toCanvasY(mouseY), scrollY) != Overlay.Result.PASS) {
            return true;
        }
        int step = (int) Math.round(scrollY * SCROLL_STEP);
        if (x >= LIST_X && x < CHAT_X) {
            list.scroll(step);
        } else {
            feed.scroll(step);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        Overlay.Result overlay = overlays.key(key);
        if (overlay != Overlay.Result.PASS) {
            return overlay == Overlay.Result.CONSUMED;
        }
        boolean enter = key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER;
        if (enter && composer.enter()) {
            return true;
        }
        if (key != InputConstants.KEY_ESCAPE && (root.keyPressed(key) || composer.keyPressed(key))) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        String text = event.codepointAsString();
        Overlay.Result overlay = overlays.type(text);
        if (overlay != Overlay.Result.PASS) {
            return overlay == Overlay.Result.CONSUMED;
        }
        boolean typed = root.charTyped(text) || composer.charTyped(text);
        if (typed && list.search().isFocused()) {
            list.searchChanged();
        }
        return typed;
    }

    // файлы, перетащенные в окно игры, пока открыта переписка, — отправить картинками
    @Override
    public void onFilesDrop(List<Path> files) {
        String partner = controller.partner();
        if (partner != null && !controller.isReadOnly()) {
            Path file = popups.media().dropped(partner, files);
            if (file != null) {
                composer.stage(file);
            }
        }
    }

    @Override
    public void removed() {
        composer.voiceStrip().cancel();
        chatMedia.player().stop();
        controller.leave(composer.input().getText());
        MenuBlur.cancel();
        Compat.stopTextInput();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private double toCanvasX(double screenX) {
        return (screenX - offsetX) / scale;
    }

    private double toCanvasY(double screenY) {
        return (screenY - offsetY) / scale;
    }
}
