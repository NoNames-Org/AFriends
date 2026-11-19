package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.core.GroupChats;
import dev.no.name.afriends.core.PrivateMessage;
import dev.no.name.afriends.gui.media.ChatMedia;
import dev.no.name.afriends.gui.media.GifFavorites;
import dev.no.name.afriends.gui.sound.UiSound;
import dev.no.name.afriends.gui.sound.UiSounds;
import dev.no.name.afriends.gui.widget.ConfirmBox;

import java.util.List;

import static dev.no.name.afriends.gui.friends.MessengerLayout.BAR_WIDTH;
import static dev.no.name.afriends.gui.friends.MessengerLayout.BAR_X;
import static dev.no.name.afriends.gui.friends.MessengerLayout.HEIGHT;
import static dev.no.name.afriends.gui.friends.MessengerLayout.WIDTH;

/**
 * Всплывающие окна мессенджера: меню, подтверждение, просмотр картинки, пикеры гифок и пересылки, редакторы
 * группы и опроса, профиль и настройки. Здесь же — как их открывать и что делать с кликом внутри них
 */
final class MessengerPopups {
    private final ChatController controller;
    private final ChatNavigator navigator;
    private final GifFavorites gifs;
    private final ChatMedia chatMedia;
    private final MediaActions media;
    private final ChatMenu menu = new ChatMenu();
    private final MessageMenu messageMenu = new MessageMenu();
    private final ConfirmBox confirmBox = new ConfirmBox();
    private final ImageViewer viewer = new ImageViewer();
    private final ForwardPicker forwardPicker = new ForwardPicker();
    private final GifPicker gifPicker;
    private final GroupEditor groupEditor;
    private final PollComposer pollComposer;
    private final GroupCreator groupCreator;
    private final ProfilePanel profilePanel;
    private final MessengerSettingsPanel settingsPanel;
    private final Forwarder forwarder;
    private final MessageActions actions;

    MessengerPopups(MessengerScreen.Context context, ChatController controller, ChatNavigator navigator,
                    ChatMedia chatMedia, MediaActions media, ComposerView composer, Selection selection) {
        this.controller = controller;
        this.navigator = navigator;
        this.gifs = context.ui().gifs();
        this.chatMedia = chatMedia;
        this.media = media;
        this.gifPicker = new GifPicker(gifs, context.ui().images());
        this.groupEditor = new GroupEditor(new MessengerHosts.GroupHost(this, controller));
        this.pollComposer = new PollComposer((chat, poll) -> controller.sendPoll(chat, poll.question(), poll.options()));
        this.groupCreator = new GroupCreator(chatMedia, this::groupCreated, controller::warn);
        this.forwarder = new Forwarder(context.route(), controller::warn, controller::info);
        this.actions = new MessageActions(context.route(), context.messages(), media, controller::warn, controller::info,
                new MessengerHosts.ActionsHost(this, controller, context.messages(), composer, selection, navigator));
        this.settingsPanel = new MessengerSettingsPanel(context.alerts(),
                new ProfileSettingsHost(context.route(), controller::warn));
        this.profilePanel = new ProfilePanel(context.route(), chatMedia, context.messages(), context.friends()::skinOf,
                new MessengerHosts.ProfileHost(this, controller, navigator));
    }

    ChatMenu menu() {
        return menu;
    }

    MessageMenu messageMenu() {
        return messageMenu;
    }

    ConfirmBox confirmBox() {
        return confirmBox;
    }

    ImageViewer viewer() {
        return viewer;
    }

    ForwardPicker forwardPicker() {
        return forwardPicker;
    }

    GifPicker gifPicker() {
        return gifPicker;
    }

    GroupEditor groupEditor() {
        return groupEditor;
    }

    PollComposer pollComposer() {
        return pollComposer;
    }

    GroupCreator groupCreator() {
        return groupCreator;
    }

    ProfilePanel profilePanel() {
        return profilePanel;
    }

    MessengerSettingsPanel settingsPanel() {
        return settingsPanel;
    }

    MediaActions media() {
        return media;
    }

    MessageActions actions() {
        return actions;
    }

    /** Меню или подтверждение поверх окна: подсветка под курсором в ленте и списке гаснет. */
    boolean blocksHover() {
        return menu.isOpen() || messageMenu.isOpen() || confirmBox.isOpen();
    }

    /** Другой чат: окошки прежнего закрываются. */
    void closeForChat() {
        messageMenu.close();
        confirmBox.close();
        forwardPicker.close();
        viewer.close();
        profilePanel.close();
        gifPicker.close();
    }

    // поверх окна в том же порядке, что и раньше; просмотр картинки и подтверждение — каждый своим слоем
    void draw(Gfx g, int mouseX, int mouseY, ChatTiles tiles) {
        menu.draw(g, mouseX, mouseY);
        messageMenu.draw(g, mouseX, mouseY);
        gifPicker.draw(g, mouseX, mouseY);
        forwardPicker.draw(g, mouseX, mouseY, controller::chatName, tiles::entry);
        groupEditor.draw(g, mouseX, mouseY);
        pollComposer.draw(g, mouseX, mouseY);
        settingsPanel.draw(g, mouseX, mouseY);
        groupCreator.draw(g, mouseX, mouseY);
        profilePanel.draw(g, mouseX, mouseY);
        if (viewer.isOpen()) {
            g.nextStratum();
            viewer.draw(g, chatMedia, WIDTH, HEIGHT, mouseX, mouseY);
        }
        if (confirmBox.isOpen()) {
            g.nextStratum();
            confirmBox.draw(g, WIDTH, HEIGHT, mouseX, mouseY);
        }
    }

    // ---- открыть ----

    void confirm(ConfirmBox.Request request) {
        confirmBox.open(request, WIDTH, HEIGHT);
    }

    // окна группы — только при живой сети: без неё состав не поменять
    void openGroupEditor(Runnable open) {
        GroupChats groups = controller.groups();
        if (groups == null || !groups.available()) {
            controller.warn("Группы работают через сеть друзей AFriends — она сейчас не подключена");
            return;
        }
        menu.close();
        open.run();
        UiSounds.play(UiSound.DROPDOWN_OPEN);
    }

    // «Позвать друга»: друзья по сети, которых ещё нет в группе
    void openInvite(String chat) {
        GroupChats groups = controller.groups();
        List<String> inGroup = groups.members(chat);
        List<String> candidates = groups.friends().stream()
                .filter(friend -> inGroup.stream().noneMatch(friend::equalsIgnoreCase)).toList();
        groupEditor.openAdd(chat, candidates, WIDTH, HEIGHT);
    }

    // клик по аватарке или нику в шапке — профиль собеседника или группы
    void openProfile(String chat) {
        navigator.blurInput();
        profilePanel.open(chat, controller.isGroup() ? ProfilePanel.Tab.MEMBERS : ProfilePanel.Tab.MEDIA, WIDTH, HEIGHT);
        UiSounds.play(UiSound.DROPDOWN_OPEN);
    }

    void openSettings() {
        navigator.blurInput();
        settingsPanel.open(WIDTH, HEIGHT);
        UiSounds.play(UiSound.CLICK);
    }

    void openNewGroup() {
        openGroupEditor(() -> {
            navigator.blurInput();
            groupCreator.open(controller.groups(), WIDTH, HEIGHT);
        });
    }

    void toggleGifs(int composerTop) {
        if (!media.available(controller.partner())) {
            controller.warn("Гифки можно отправлять только друзьям по сети");
            return;
        }
        gifPicker.toggle(BAR_X + BAR_WIDTH, composerTop);
    }

    // выйти из группы — после подтверждения; открытый чат этой группы закрывается
    void confirmLeave(String chat, String title) {
        confirm(new ConfirmBox.Request("Выйти из группы «" + title
                + "»? Переписка у тебя удалится, вернуть может только участник.", null, false, "Выйти", true,
                checked -> {
                    controller.groups().leave(chat, controller::warn);
                    if (chat.equals(controller.partner())) {
                        navigator.select(null);
                    }
                }));
    }

    // новая группа из окна создания: сразу открыть её
    void groupCreated(String chat, String title) {
        navigator.select(chat);
        navigator.focusInput();
        controller.info("Группа «" + title + "» создана");
    }

    // поле названия группы забирало ввод текста и, закрываясь, выключило его: возвращаем полю сообщения
    boolean afterEditor(boolean handled) {
        if (!groupEditor.isOpen() && !pollComposer.isOpen() && !settingsPanel.isOpen() && !groupCreator.isOpen()
                && !profilePanel.isOpen()) {
            navigator.refocusInput();
        }
        return handled;
    }

    // ---- клики внутри окошек ----

    void clickViewer(ImageViewer.Action action) {
        switch (action) {
            case SAVE -> media.save(viewer.image());
            case OPEN -> media.open(viewer.image());
            case CLOSE -> viewer.close();
            case NONE -> { }
        }
        UiSounds.play(UiSound.CLICK);
    }

    // окно пересылки: мимо — закрыть, по чату — переслать выделенное туда
    void clickForward(double x, double y, Selection selection) {
        String target = forwardPicker.click(x, y);
        List<PrivateMessage> forwarded = forwardPicker.messages();
        if (target == null || !target.isEmpty()) {
            forwardPicker.close();
        }
        if (target != null && !target.isEmpty()) {
            String partner = controller.partner();
            forwarded.forEach(message -> forwarder.forward(message, controller.chatName(partner), target,
                    controller.chatName(target)));
            selection.clear();
            UiSounds.play(UiSound.SELECT);
        }
    }

    // панель гифок: мимо панели — просто закрыть, клик дальше не идёт
    void clickGifs(double x, double y, boolean right) {
        GifPicker.Click click = gifPicker.click(x, y, right);
        if (click == null) {
            gifPicker.close();
            return;
        }
        switch (click) {
            case GifPicker.Send send -> {
                gifPicker.close();
                String partner = controller.partner();
                if (partner != null && media.available(partner)) {
                    media.send(partner, send.gif().file(), "");
                }
            }
            case GifPicker.Remove remove -> {
                gifs.remove(remove.gif().sha256());
                controller.info("Гифка убрана из избранного");
            }
            case GifPicker.Inside inside -> { }
        }
        UiSounds.play(UiSound.CLICK);
    }
}
