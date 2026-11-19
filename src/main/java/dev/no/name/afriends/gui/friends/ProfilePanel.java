package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.gui.media.ChatMedia;
import com.mojang.blaze3d.platform.InputConstants;
import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.core.GroupChats;
import dev.no.name.afriends.core.MessageImage;
import dev.no.name.afriends.core.MessageRoute;
import dev.no.name.afriends.core.PrivateMessage;
import dev.no.name.afriends.core.PrivateMessages;
import dev.no.name.afriends.core.ProfileRoute;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.TextWrap;
import dev.no.name.afriends.gui.render.icon.Icon;
import dev.no.name.afriends.gui.render.text.TextStyle;
import dev.no.name.afriends.gui.sound.UiSound;
import dev.no.name.afriends.gui.sound.UiSounds;
import dev.no.name.afriends.gui.widget.ConfirmBox;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.List;
import java.util.function.Function;

/**
 * Профиль поверх переписки, как в Telegram (клик по аватарке или нику в шапке). У игрока: голова, ник, строка
 * статуса, «О себе», «Написать» и блокировка; вкладки «Медиа» и «Голосовые». У группы: аватарка, название,
 * описание, сколько участников, кнопки по правам (название, описание, аватарка, позвать друга) и вкладки
 * «Участники», «Медиа», «Голосовые». Клик мимо или Esc — закрыть
 */
final class ProfilePanel {
    private static final int WIDTH = 270;
    private static final int HEIGHT = 270;
    private static final int PAD = 8;
    private static final int AVATAR = 40;
    private static final int LINE = 10;
    private static final int MAX_LINES = 3;

    enum Tab { MEMBERS, MEDIA, VOICE }

    /** Что панель просит у окна переписки. */
    interface Host {
        void confirm(ConfirmBox.Request request);

        void view(MessageImage image);

        void invite(String chat);

        // «Написать»: закрыть профиль и поставить курсор в поле сообщения
        void write();

        void warn(String text);

        void info(String text);

        // закрыть профиль и прокрутить переписку к сообщению (старое догружается)
        void jumpTo(String messageKey);
    }

    private final MessageRoute route;
    private final ChatMedia chatMedia;
    private final PrivateMessages messages;
    private final Function<String, PlayerSkin> skins;
    private final Host host;
    private final MemberEditor memberEditor;
    private final ProfileMembers members = new ProfileMembers();
    private final ProfileMedia mediaTab = new ProfileMedia(ProfileMedia.Kind.MEDIA);
    private final ProfileMedia voiceTab = new ProfileMedia(ProfileMedia.Kind.VOICE);
    private final ProfileActions actions;
    private String chat;
    private boolean group;
    private Tab tab;
    private int areaWidth;
    private int areaHeight;
    private float x;
    private float y;
    private List<String> aboutLines = List.of();
    private float actionsY;
    private float tabsY;
    private float contentY;

    ProfilePanel(MessageRoute route, ChatMedia chatMedia, PrivateMessages messages, Function<String, PlayerSkin> skins,
                 Host host) {
        this.route = route;
        this.chatMedia = chatMedia;
        this.messages = messages;
        this.skins = skins;
        this.host = host;
        this.memberEditor = new MemberEditor(host::warn);
        this.actions = new ProfileActions(route, messages, host);
    }

    boolean isOpen() {
        return chat != null;
    }

    void open(String chat, Tab tab, int areaWidth, int areaHeight) {
        this.chat = chat;
        this.group = PrivateMessages.isGroup(chat);
        this.tab = group || tab != Tab.MEMBERS ? tab : Tab.MEDIA;
        this.areaWidth = areaWidth;
        this.areaHeight = areaHeight;
        actions.reset(chat);
        memberEditor.close();
        members.reset();
        mediaTab.reset();
        voiceTab.reset();
        x = (areaWidth - WIDTH) / 2f;
        y = (areaHeight - HEIGHT) / 2f;
    }

    void close() {
        chat = null;
        actions.blur();
        mediaTab.blur();
        voiceTab.blur();
        memberEditor.close();
    }

    private GroupChats groups() {
        return route.groups();
    }

    private ProfileRoute profile() {
        return route.profile();
    }

    private Tab[] tabs() {
        return group ? new Tab[] {Tab.MEMBERS, Tab.MEDIA, Tab.VOICE} : new Tab[] {Tab.MEDIA, Tab.VOICE};
    }

    private static String tabLabel(Tab tab) {
        return switch (tab) {
            case MEMBERS -> "Участники";
            case MEDIA -> "Медиа";
            case VOICE -> "Голосовые";
        };
    }

    private static String selfName() {
        return Minecraft.getInstance().getUser().getName();
    }

    // ---- раскладка ----

    private String aboutText() {
        if (group) {
            return messages.description(chat);
        }
        ProfileRoute profile = profile();
        String about = profile == null ? "" : profile.aboutOf(messages.displayName(chat));
        return about == null ? "" : about;
    }

    private void layout() {
        String about = aboutText().strip();
        List<String> lines = about.isEmpty() ? List.of() : TextWrap.lines(about, WIDTH - PAD * 2);
        aboutLines = lines.size() > MAX_LINES ? lines.subList(0, MAX_LINES) : lines;
        float aboutY = y + PAD + AVATAR + 6;
        float aboutBottom = aboutLines.isEmpty() ? aboutY : aboutY + LINE + aboutLines.size() * LINE + 2;
        actionsY = aboutBottom + 2;
        actions.layout(x + PAD, actionsY, WIDTH - PAD * 2);
        tabsY = actionsY + (actions.isVisible() ? PanelKit.BUTTON + 6 : 0);
        contentY = tabsY + PanelKit.BUTTON + 6;
        float contentHeight = y + HEIGHT - PAD - contentY;
        members.layout(x + PAD, contentY, WIDTH - PAD * 2, contentHeight);
        mediaTab.layout(x + PAD, contentY, WIDTH - PAD * 2, contentHeight);
        voiceTab.layout(x + PAD, contentY, WIDTH - PAD * 2, contentHeight);
    }

    // ---- рисование ----

    void draw(Gfx g, int mouseX, int mouseY) {
        if (chat == null) {
            return;
        }
        layout();
        int hoverX = memberEditor.isOpen() ? -1000 : mouseX;
        Draw.roundedRect(g, 0, 0, areaWidth, areaHeight, Theme.PANEL_RADIUS, 0x66000000);
        PanelKit.frame(g, x, y, WIDTH, HEIGHT);
        drawHeader(g, hoverX, mouseY);
        actions.draw(g, hoverX, mouseY);
        Tab[] tabs = tabs();
        String[] labels = new String[tabs.length];
        int active = 0;
        for (int i = 0; i < tabs.length; i++) {
            labels[i] = tabLabel(tabs[i]);
            active = tabs[i] == tab ? i : active;
        }
        PanelKit.tabs(g, labels, active, x + PAD, tabsY, WIDTH - PAD * 2, hoverX, mouseY);
        switch (tab) {
            case MEMBERS -> {
                members.load(groups(), chat, selfName());
                members.draw(g, groups(), chat, skins, hoverX, mouseY);
            }
            case MEDIA -> {
                mediaTab.load(messages.chat(chat), this::authorOf);
                mediaTab.draw(g, chatMedia, hoverX, mouseY);
            }
            case VOICE -> {
                voiceTab.load(messages.chat(chat), this::authorOf);
                voiceTab.draw(g, chatMedia, hoverX, mouseY);
            }
        }
        memberEditor.draw(g, mouseX, mouseY);
    }

    private String authorOf(PrivateMessage message) {
        if (message.fromMe()) {
            return "Ты";
        }
        return message.author() != null ? message.author() : messages.displayName(chat);
    }

    private void drawHeader(Gfx g, int mouseX, int mouseY) {
        float avatarX = x + PAD;
        float avatarY = y + PAD;
        String title = messages.displayName(chat);
        String status;
        if (group) {
            PanelKit.groupTile(g, messages.avatar(chat), chatMedia, avatarX, avatarY, AVATAR);
            GroupChats groups = groups();
            int count = groups == null ? 0 : groups.members(chat).size() + 1;
            status = count > 0 ? count + " " + members(count) : "Групповой чат";
        } else {
            PanelKit.face(g, skins.apply(title), avatarX, avatarY, AVATAR);
            String line = route.headerLine(chat);
            status = line != null ? line : "Не в сети AFriends · через /msg сервера";
        }
        float textX = avatarX + AVATAR + 8;
        int textWidth = (int) (x + WIDTH - PAD - 14 - textX);
        Draw.text(g, Draw.trim(title, textWidth, TextStyle.TITLE), textX, avatarY + 9, Theme.text(), TextStyle.TITLE);
        Draw.text(g, Draw.trim(status, textWidth, TextStyle.SMALL), textX, avatarY + 24, Theme.textSoft(), TextStyle.SMALL);
        float closeX = x + WIDTH - PAD - 10;
        boolean closeHover = PanelKit.inside(mouseX, mouseY, closeX - 2, y + PAD - 2, 14, 14);
        Draw.icon(g, Icon.CLOSE, closeX, y + PAD, 10, closeHover ? Theme.text() : Theme.textDim());
        if (!aboutLines.isEmpty()) {
            float aboutY = y + PAD + AVATAR + 6;
            Draw.text(g, group ? "Описание" : "О себе", x + PAD, aboutY, Theme.textDim(), TextStyle.SMALL);
            for (int i = 0; i < aboutLines.size(); i++) {
                Draw.text(g, aboutLines.get(i), x + PAD, aboutY + LINE + i * LINE, Theme.text());
            }
        }
    }

    private static String members(int count) {
        int last = count % 10;
        int lastTwo = count % 100;
        if (last == 1 && lastTwo != 11) {
            return "участник";
        }
        return last >= 2 && last <= 4 && (lastTwo < 12 || lastTwo > 14) ? "участника" : "участников";
    }

    // ---- действия ----

    private void clickMember(ProfileMembers.Click click) {
        GroupChats groups = groups();
        if (groups == null) {
            return;
        }
        String target = chat;
        Runnable kick = () -> host.confirm(new ConfirmBox.Request("Исключить " + click.member() + " из группы?", null, false,
                "Исключить", true, checked -> groups.kick(target, click.member(), host::warn)));
        if (click.kick()) {
            kick.run();
            return;
        }
        memberEditor.open(groups, chat, click.member(), click.self(), kick, areaWidth, areaHeight);
        UiSounds.play(UiSound.CLICK);
    }

    // true — клик поглощён; мимо окна — закрыть
    boolean click(double mouseX, double mouseY) {
        if (chat == null) {
            return false;
        }
        if (memberEditor.isOpen()) {
            return memberEditor.click(mouseX, mouseY);
        }
        layout();
        if (!PanelKit.inside(mouseX, mouseY, x, y, WIDTH, HEIGHT)
                || PanelKit.inside(mouseX, mouseY, x + WIDTH - PAD - 12, y + PAD - 2, 14, 14)) {
            close();
            return true;
        }
        if (actions.click(mouseX, mouseY)) {
            return true;
        }
        Tab[] tabs = tabs();
        int index = PanelKit.tabAt(mouseX, mouseY, tabs.length, x + PAD, tabsY, WIDTH - PAD * 2);
        if (index >= 0) {
            tab = tabs[index];
            mediaTab.blur();
            voiceTab.blur();
            UiSounds.play(UiSound.SELECT);
            return true;
        }
        switch (tab) {
            case MEMBERS -> {
                ProfileMembers.Click click = members.click(mouseX, mouseY);
                if (click != null) {
                    clickMember(click);
                }
            }
            case MEDIA -> {
                ProfileMedia.Click click = mediaTab.click(mouseX, mouseY, chatMedia);
                if (click != null && click.view() != null) {
                    host.view(click.view());
                    UiSounds.play(UiSound.CLICK);
                }
                jumpIfAsked(click);
            }
            case VOICE -> jumpIfAsked(voiceTab.click(mouseX, mouseY, chatMedia));
        }
        return true;
    }

    private void jumpIfAsked(ProfileMedia.Click click) {
        if (click != null && click.jumpTo() != null) {
            host.jumpTo(click.jumpTo());
        }
    }

    private ProfileMedia activeMedia() {
        return tab == Tab.MEDIA ? mediaTab : tab == Tab.VOICE ? voiceTab : null;
    }

    boolean keyPressed(int key) {
        if (chat == null) {
            return false;
        }
        if (memberEditor.isOpen()) {
            return memberEditor.keyPressed(key);
        }
        if (actions.keyPressed(key)) {
            return true;
        }
        if (key == InputConstants.KEY_ESCAPE) {
            close();
            return true;
        }
        ProfileMedia media = activeMedia();
        if (media != null) {
            media.keyPressed(key);
        }
        return true;
    }

    boolean charTyped(String text) {
        if (chat == null) {
            return false;
        }
        if (memberEditor.isOpen()) {
            return memberEditor.charTyped(text);
        }
        if (actions.charTyped(text)) {
            return true;
        }
        ProfileMedia media = activeMedia();
        if (media != null) {
            media.charTyped(text);
        }
        return true;
    }

    boolean scroll(double mouseX, double mouseY, double amount) {
        if (chat == null) {
            return false;
        }
        if (!memberEditor.isOpen() && mouseY >= contentY) {
            switch (tab) {
                case MEMBERS -> members.scroll(amount);
                case MEDIA -> mediaTab.scroll(amount);
                case VOICE -> voiceTab.scroll(amount);
            }
        }
        return true;
    }
}
