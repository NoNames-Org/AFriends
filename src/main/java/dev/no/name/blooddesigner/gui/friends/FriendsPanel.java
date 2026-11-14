package dev.no.name.blooddesigner.gui.friends;

import com.mojang.blaze3d.platform.InputConstants;
import dev.no.name.blooddesigner.compat.Gfx;
import dev.no.name.blooddesigner.compat.WidgetBase;
import dev.no.name.blooddesigner.core.Friend;
import dev.no.name.blooddesigner.core.FriendManager;
import dev.no.name.blooddesigner.core.IgnoreList;
import dev.no.name.blooddesigner.core.PrivateMessages;
import dev.no.name.blooddesigner.gui.Theme;
import dev.no.name.blooddesigner.gui.animation.Animated;
import dev.no.name.blooddesigner.gui.animation.Motion;
import dev.no.name.blooddesigner.gui.render.Colors;
import dev.no.name.blooddesigner.gui.render.Draw;
import dev.no.name.blooddesigner.gui.render.icon.Icon;
import dev.no.name.blooddesigner.gui.render.text.TextStyle;
import dev.no.name.blooddesigner.gui.sound.UiSound;
import dev.no.name.blooddesigner.gui.sound.UiSounds;
import dev.no.name.blooddesigner.gui.widget.TextFieldWidget;
import dev.no.name.blooddesigner.gui.widget.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Matrix3x2fStack;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class FriendsPanel extends WidgetBase {
    private static final int PADDING = 8;
    private static final int HEADER_HEIGHT = 40;
    private static final int OWN_FACE = 26;
    private static final int FIELD_HEIGHT = 18;
    private static final int ROW_HEIGHT = 24;
    private static final int FACE = 18;
    private static final int SCROLL_STEP = 16;
    private static final int NAME_LENGTH = 16;
    private static final long NOTICE_MS = 3000;
    private static final int SUCCESS = 0xFF5CFFB0;
    private static final int DANGER = 0xFFFF6B6B;
    private static final Pattern NAME_CHAR = Pattern.compile("[A-Za-z0-9_]");

    private final FriendManager friends;
    private final PrivateMessages messages;
    private final IgnoreList ignored;
    private final FriendsState state;
    private final Runnable save;
    private final int panelWidth;
    private final TextFieldWidget field = new TextFieldWidget(Icon.SEARCH, "Ник игрока").withMaxLength(NAME_LENGTH);
    private final Animated scrollView = new Animated(0, Motion.FAST);
    private final Animated addHover = new Animated(0, Motion.FAST);
    private int scroll;
    private int contentHeight;
    private boolean takeFocus;

    private String notice = "";
    private int noticeColor;
    private long noticeAt;

    public FriendsPanel(int x, int y, int width, int height, FriendManager friends, PrivateMessages messages,
                        IgnoreList ignored, FriendsState state, Runnable save) {
        super(x, y, width, height, Component.literal("Список друзей"));
        this.panelWidth = width;
        this.friends = friends;
        this.messages = messages;
        this.ignored = ignored;
        this.state = state;
        this.save = save;
    }

    @Override
    protected void draw(Gfx g, int mouseX, int mouseY, float partialTick) {
        float open = state.progress();
        if (open <= 0.01f) {
            return;
        }
        Draw.setPixelScale(Minecraft.getInstance().getWindow().getGuiScale());
        int mx = isMouseOver(mouseX, mouseY) ? mouseX : -1000;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();

        pose.translate((1 - open) * (panelWidth + 8), 0);
        Draw.pushAlpha(open);
        Draw.shadow(g, getX(), getY() + 2, panelWidth, height, Theme.PANEL_RADIUS, 14, Colors.fade(Theme.SHADOW, 0.7f));
        Draw.gradient(g, getX(), getY(), panelWidth, height, Theme.PANEL_RADIUS,
                Colors.fade(Theme.PANEL_TOP, 0.92f), Colors.fade(Theme.PANEL_BOTTOM, 0.92f));
        Draw.border(g, getX(), getY(), panelWidth, height, Theme.PANEL_RADIUS, Theme.BORDER);
        drawHeader(g);
        drawSearch(g, mx, mouseY);
        drawList(g, mx, mouseY);
        Draw.popAlpha();
        pose.popMatrix();
    }

    private void drawHeader(Gfx g) {
        LocalPlayer self = Minecraft.getInstance().player;
        int faceX = getX() + PADDING;
        int faceY = getY() + (HEADER_HEIGHT - OWN_FACE) / 2;
        if (self != null) {
            Draw.roundedRect(g, faceX - 1, faceY - 1, OWN_FACE + 2, OWN_FACE + 2, 3, Theme.BORDER_HOVER);
            g.face(self.getSkin(), faceX, faceY, OWN_FACE);
        }
        float textX = faceX + OWN_FACE + 8;
        String name = self == null ? "—" : self.getGameProfile().name();
        Draw.text(g, Draw.trim(name, (int) (getX() + panelWidth - PADDING - textX)), textX, getY() + 13, Theme.TEXT,
                TextStyle.BODY_MEDIUM);
        Draw.text(g, "Твой профиль", textX, getY() + 25, Theme.TEXT_DIM, TextStyle.SMALL);
        Draw.rect(g, getX() + PADDING, getY() + HEADER_HEIGHT, panelWidth - PADDING * 2, 1, Theme.BORDER);
    }

    private int labelY() {
        return getY() + HEADER_HEIGHT + 8;
    }

    private int fieldY() {
        return labelY() + 13;
    }

    private int addX() {
        return getX() + panelWidth - PADDING - FIELD_HEIGHT;
    }

    private void drawSearch(Gfx g, int mouseX, int mouseY) {
        int x = getX() + PADDING;
        boolean showNotice = !notice.isEmpty() && System.currentTimeMillis() - noticeAt < NOTICE_MS;
        if (showNotice) {
            Draw.text(g, Draw.trim(notice, panelWidth - PADDING * 2), x, labelY(), noticeColor, TextStyle.SMALL);
        } else {
            Draw.text(g, "Друзья", x, labelY(), Theme.TEXT, TextStyle.SMALL);
            Draw.text(g, String.valueOf(friends.size()), x + Draw.width("Друзья", TextStyle.SMALL) + 5, labelY(),
                    Theme.TEXT_DIM, TextStyle.SMALL);
        }
        field.setBounds(x, fieldY(), addX() - 4 - x, FIELD_HEIGHT);
        field.render(g, mouseX, mouseY);

        boolean hovered = mouseX >= addX() && mouseX < addX() + FIELD_HEIGHT && mouseY >= fieldY() && mouseY < fieldY() + FIELD_HEIGHT;
        addHover.setTarget(hovered);
        int fill = Colors.mix(Theme.ACCENT, Colors.mix(Theme.ACCENT, 0xFFFFFFFF, 0.25f), addHover.get());
        Draw.roundedRect(g, addX(), fieldY(), FIELD_HEIGHT, FIELD_HEIGHT, Theme.RADIUS, fill);
        Draw.icon(g, Icon.PLUS, addX() + (FIELD_HEIGHT - Theme.ICON_SMALL) / 2f, fieldY() + (FIELD_HEIGHT - Theme.ICON_SMALL) / 2f,
                Theme.ICON_SMALL, Theme.ACCENT_TEXT);
    }

    private void addFromField() {
        String name = field.getText().strip();
        if (name.isEmpty()) {
            notify("Впиши ник, чтобы добавить", DANGER);
            return;
        }
        switch (friends.add(name)) {
            case ADDED -> {
                save.run();
                field.setText("");
                notify(name + " добавлен в друзья", SUCCESS);
                UiSounds.play(UiSound.SELECT);
            }
            case ALREADY -> notify(name + " уже в друзьях", Theme.TEXT_SOFT);
            case SELF -> notify("Себя добавить нельзя", DANGER);
            case INVALID -> notify("Это не похоже на ник", DANGER);
        }
    }

    private void notify(String text, int color) {
        notice = text;
        noticeColor = color;
        noticeAt = System.currentTimeMillis();
    }

    private List<Friend> visible() {
        String query = field.getText().strip().toLowerCase(Locale.ROOT);
        List<Friend> all = friends.sorted();
        return query.isEmpty() ? all : all.stream().filter(friend -> friend.name().toLowerCase(Locale.ROOT).contains(query)).toList();
    }

    private int listTop() {
        return fieldY() + FIELD_HEIGHT + 8;
    }

    private int listBottom() {
        return getY() + height - 4;
    }

    private void drawList(Gfx g, int mouseX, int mouseY) {
        List<Friend> list = visible();
        int top = listTop();
        int bottom = listBottom();
        contentHeight = list.size() * ROW_HEIGHT;
        clampScroll(bottom - top);
        if (list.isEmpty()) {
            drawEmpty(g, top, bottom);
            return;
        }
        int shownScroll = Math.round(scrollView.get());
        g.enableScissor(getX(), top, getX() + panelWidth, bottom);
        for (int i = 0; i < list.size(); i++) {
            int rowY = top + i * ROW_HEIGHT - shownScroll;
            if (rowY + ROW_HEIGHT < top || rowY > bottom) {
                continue;
            }
            drawRow(g, list.get(i), rowY, mouseX, mouseY);
        }
        g.disableScissor();
    }

    private void drawRow(Gfx g, Friend friend, int rowY, int mouseX, int mouseY) {
        int rowX = getX() + 4;
        int rowWidth = panelWidth - 8;
        boolean hovered = mouseX >= rowX && mouseX < rowX + rowWidth && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
        boolean selected = state.menuFriend() != null && state.menuFriend().name().equalsIgnoreCase(friend.name());
        if (selected) {
            Draw.box(g, rowX, rowY, rowWidth, ROW_HEIGHT, Theme.RADIUS, Theme.CARD_SELECTED, Theme.BORDER_HOVER);
        } else if (hovered) {
            Draw.roundedRect(g, rowX, rowY, rowWidth, ROW_HEIGHT, Theme.RADIUS, Theme.CARD_HOVER);
        }
        int faceX = getX() + PADDING + 2;
        int faceY = rowY + (ROW_HEIGHT - FACE) / 2;
        drawFace(g, friends.skinOf(friend), faceX, faceY);

        float textX = faceX + FACE + 8;
        int unread = messages.unread(friend.name());
        float badgeWidth = unread > 0 ? Draw.width(String.valueOf(unread), TextStyle.SMALL) + 8 : 0;
        int textWidth = (int) (getX() + panelWidth - PADDING - textX - badgeWidth - (badgeWidth > 0 ? 4 : 0));
        boolean isIgnored = ignored.isIgnored(friend.name());
        float nameY = isIgnored ? rowY + 5 : rowY + (ROW_HEIGHT - 8) / 2f;
        Draw.text(g, Draw.trim(friend.name(), textWidth), textX, nameY, isIgnored ? Theme.TEXT_SOFT : Theme.TEXT, TextStyle.BODY_MEDIUM);
        if (isIgnored) {
            Draw.text(g, "Игнорируется", textX, rowY + 14, Theme.TEXT_FAINT, TextStyle.SMALL);
        }
        if (unread > 0) {
            float badgeX = getX() + panelWidth - PADDING - badgeWidth;
            float badgeY = rowY + (ROW_HEIGHT - 10) / 2f;
            Draw.roundedRect(g, badgeX, badgeY, badgeWidth, 10, 5, Theme.ACCENT);
            Draw.text(g, String.valueOf(unread), badgeX + 4, badgeY + 2, Theme.ACCENT_TEXT, TextStyle.SMALL);
        }
    }

    private static void drawFace(Gfx g, PlayerSkin skin, int x, int y) {
        if (skin != null) {
            g.face(skin, x, y, FACE);
        } else {
            Draw.gradient(g, x, y, FACE, FACE, 2, Theme.TILE_ON, Theme.TILE);
            Draw.icon(g, Icon.USER, x + (FACE - 10) / 2f, y + (FACE - 10) / 2f, 10, Theme.TEXT_DIM);
        }
    }

    private void drawEmpty(Gfx g, int top, int bottom) {
        float centerX = getX() + panelWidth / 2f;
        float centerY = (top + bottom) / 2f;
        boolean searching = !field.getText().isBlank();
        Draw.icon(g, searching ? Icon.SEARCH : Icon.USERS, centerX - 9, centerY - 26, 18, Theme.TEXT_FAINT);
        Draw.textCentered(g, searching ? "Никого не нашлось" : "Друзей пока нет", centerX, centerY - 2, Theme.TEXT_DIM);
        String hint = searching ? "Жми «+», чтобы добавить" : "Впиши ник выше и жми «+»";
        Draw.text(g, hint, centerX - Draw.width(hint, TextStyle.SMALL) / 2f, centerY + 10, Theme.TEXT_FAINT, TextStyle.SMALL);
    }

    public boolean scroll(double mouseX, double mouseY, double amount) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        int listHeight = listBottom() - listTop();
        scroll = (int) Math.max(0, Math.min(Math.max(0, contentHeight - listHeight), scroll - amount * SCROLL_STEP));
        scrollView.setTarget(scroll);
        return true;
    }

    private void clampScroll(int listHeight) {
        int maxScroll = Math.max(0, contentHeight - listHeight);
        if (scroll > maxScroll) {
            scroll = maxScroll;
            scrollView.setTarget(scroll);
        }
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return state.isOpen() && state.progress() > 0.99f && super.isMouseOver(mouseX, mouseY);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        field.mouseClicked(mouseX, mouseY, Widget.LEFT_BUTTON);
        takeFocus = field.isFocused();
        if (mouseX >= addX() && mouseX < addX() + FIELD_HEIGHT && mouseY >= fieldY() && mouseY < fieldY() + FIELD_HEIGHT) {
            UiSounds.play(UiSound.CLICK);
            addFromField();
            return;
        }
        if (mouseY < listTop()) {
            return;
        }
        List<Friend> list = visible();
        int index = (int) Math.floor((mouseY - listTop() + Math.round(scrollView.get())) / ROW_HEIGHT);
        if (index < 0 || index >= list.size()) {
            state.closeMenu();
            return;
        }
        Friend friend = list.get(index);
        if (state.menuFriend() != null && state.menuFriend().name().equalsIgnoreCase(friend.name())) {
            state.closeMenu();
            return;
        }
        UiSounds.play(UiSound.CLICK);
        int rowY = listTop() + index * ROW_HEIGHT - Math.round(scrollView.get());
        state.openMenu(friend, rowY);
    }

    public boolean handleKey(KeyEvent event) {
        if (!field.isFocused() || !state.isOpen()) {
            return false;
        }
        int key = event.key();
        if (key == InputConstants.KEY_ESCAPE) {
            return false;
        }
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            addFromField();
            return true;
        }
        field.keyPressed(key);
        resetScroll();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return handleKey(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!field.isFocused()) {
            return false;
        }
        String typed = event.codepointAsString();
        if (NAME_CHAR.matcher(typed).matches()) {
            field.charTyped(typed);
            resetScroll();
        }
        return true;
    }

    private void resetScroll() {
        scroll = 0;
        scrollView.setTarget(0);
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) {
            field.blur();
        } else {
            field.refocus();
        }
    }

    public void blur() {
        field.blur();
    }

    @Override
    public boolean shouldTakeFocusAfterInteraction() {
        return takeFocus;
    }

    public int panelWidth() {
        return panelWidth;
    }
}
