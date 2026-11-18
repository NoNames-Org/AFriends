package dev.no.name.afriends.gui.friends;

import com.mojang.blaze3d.platform.InputConstants;
import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.core.GroupChats;
import dev.no.name.afriends.core.GroupPermissions;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.icon.Icon;
import dev.no.name.afriends.gui.render.text.TextStyle;
import dev.no.name.afriends.gui.sound.UiSound;
import dev.no.name.afriends.gui.sound.UiSounds;
import dev.no.name.afriends.gui.widget.TextFieldWidget;
import dev.no.name.afriends.gui.widget.Widget;

import java.util.function.Consumer;

/**
 * Окно владельца над профилем группы: права участника галочками (подправа сдвинуты и тусклые, пока выключена
 * «Отправка сообщений»), префикс до 16 символов и «Исключить». Для себя — только префикс.
 * «Сохранить» или Enter — отправить, клик мимо или Esc — закрыть без изменений
 */
final class MemberEditor {
    static final int MAX_PREFIX = 16;
    private static final int WIDTH = 220;
    private static final int PAD = 8;
    private static final int TITLE = 14;
    private static final int FIELD = 16;
    private static final int ROW = 13;
    private static final int INDENT = 10;

    private final TextFieldWidget prefix = new TextFieldWidget(Icon.TAG, "Префикс, например Админ").withMaxLength(MAX_PREFIX);
    private final Consumer<String> warn;
    private GroupChats groups;
    private String chat;
    private String member;
    private boolean self;
    private int perms;
    private int savedPerms;
    private String savedPrefix = "";
    private Runnable kick;
    private float x;
    private float y;
    private float height;

    MemberEditor(Consumer<String> warn) {
        this.warn = warn;
    }

    boolean isOpen() {
        return member != null;
    }

    // self — своя строка владельца: прав себе не выдать, только префикс. kick — спросить и исключить
    void open(GroupChats groups, String chat, String member, boolean self, Runnable kick, int areaWidth, int areaHeight) {
        this.groups = groups;
        this.chat = chat;
        this.member = member;
        this.self = self;
        this.kick = kick;
        savedPerms = self ? GroupPermissions.ALL : groups.permsOf(chat, member);
        perms = savedPerms;
        savedPrefix = groups.prefixOf(chat, member);
        prefix.setText(savedPrefix == null ? "" : savedPrefix);
        height = PAD * 2 + TITLE + FIELD + 6 + (self ? 0 : GroupPermissions.ENTRIES.size() * ROW + 6) + PanelKit.BUTTON;
        x = (areaWidth - WIDTH) / 2f;
        y = (areaHeight - height) / 2f;
        prefix.setBounds((int) x + PAD, (int) y + PAD + TITLE, WIDTH - PAD * 2, FIELD);
        prefix.focus();
    }

    void close() {
        member = null;
        prefix.blur();
    }

    private float listTop() {
        return y + PAD + TITLE + FIELD + 6;
    }

    private float buttonY() {
        return y + height - PAD - PanelKit.BUTTON;
    }

    private float saveX() {
        return x + WIDTH - PAD - 60;
    }

    private float cancelX() {
        return saveX() - 4 - 56;
    }

    void draw(Gfx g, int mouseX, int mouseY) {
        if (member == null) {
            return;
        }
        PanelKit.frame(g, x, y, WIDTH, height);
        String heading = self ? "Твой префикс в группе" : "Права: " + member;
        Draw.text(g, Draw.trim(heading, WIDTH - PAD * 2, TextStyle.SMALL), x + PAD, y + PAD, Theme.TEXT_SOFT, TextStyle.SMALL);
        prefix.render(g, mouseX, mouseY);
        if (!self) {
            drawEntries(g, mouseX, mouseY);
            PanelKit.button(g, "Исключить", x + PAD, buttonY(), 60, false, true, false, mouseX, mouseY);
        }
        PanelKit.button(g, "Отмена", cancelX(), buttonY(), 56, false, false, false, mouseX, mouseY);
        PanelKit.button(g, "Сохранить", saveX(), buttonY(), 60, true, false, false, mouseX, mouseY);
    }

    private void drawEntries(Gfx g, int mouseX, int mouseY) {
        float rowY = listTop();
        boolean sending = (perms & GroupPermissions.SEND) != 0;
        for (GroupPermissions.Entry entry : GroupPermissions.ENTRIES) {
            boolean disabled = entry.isChild() && !sending;
            float rowX = x + PAD + (entry.isChild() ? INDENT : 0);
            boolean hover = !disabled && PanelKit.inside(mouseX, mouseY, x + PAD, rowY, WIDTH - PAD * 2, ROW);
            if (hover) {
                Draw.roundedRect(g, x + PAD - 2, rowY, WIDTH - PAD * 2 + 4, ROW, Theme.RADIUS, Theme.CARD_HOVER);
            }
            PanelKit.checkbox(g, (perms & entry.bit()) != 0, disabled, rowX, rowY + 2.5f);
            int color = disabled ? Theme.TEXT_FAINT : Theme.TEXT;
            Draw.text(g, Draw.trim(entry.title(), (int) (x + WIDTH - PAD - rowX - 12), TextStyle.SMALL), rowX + 12, rowY + 4,
                    color, TextStyle.SMALL);
            rowY += ROW;
        }
    }

    // true — клик поглощён (окно модальное); мимо окна — закрыть
    boolean click(double mouseX, double mouseY) {
        if (member == null) {
            return false;
        }
        if (!PanelKit.inside(mouseX, mouseY, x, y, WIDTH, height)) {
            close();
            return true;
        }
        if (prefix.mouseClicked(mouseX, mouseY, Widget.LEFT_BUTTON)) {
            return true;
        }
        float buttonY = buttonY();
        if (PanelKit.inside(mouseX, mouseY, saveX(), buttonY, 60, PanelKit.BUTTON)) {
            save();
        } else if (PanelKit.inside(mouseX, mouseY, cancelX(), buttonY, 56, PanelKit.BUTTON)) {
            close();
        } else if (!self && PanelKit.inside(mouseX, mouseY, x + PAD, buttonY, 60, PanelKit.BUTTON)) {
            Runnable action = kick;
            close();
            action.run();
        } else if (!self) {
            clickEntry(mouseY);
        }
        return true;
    }

    private void clickEntry(double mouseY) {
        int index = (int) Math.floor((mouseY - listTop()) / ROW);
        if (mouseY < listTop() || index < 0 || index >= GroupPermissions.ENTRIES.size()) {
            return;
        }
        GroupPermissions.Entry entry = GroupPermissions.ENTRIES.get(index);
        if (entry.isChild() && (perms & GroupPermissions.SEND) == 0) {
            return;
        }
        perms = GroupPermissions.toggle(perms, entry.bit());
        UiSounds.play(UiSound.SELECT);
    }

    private void save() {
        String text = prefix.getText().replaceAll("\\s+", " ").strip();
        if (!text.equals(savedPrefix == null ? "" : savedPrefix)) {
            groups.setPrefix(chat, member, text, warn);
        }
        if (!self && perms != savedPerms) {
            groups.setPerms(chat, member, perms, warn);
        }
        UiSounds.play(UiSound.CLICK);
        close();
    }

    boolean keyPressed(int key) {
        if (member == null) {
            return false;
        }
        if (key == InputConstants.KEY_ESCAPE) {
            close();
            return true;
        }
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            save();
            return true;
        }
        prefix.keyPressed(key);
        return true;
    }

    boolean charTyped(String text) {
        if (member == null) {
            return false;
        }
        prefix.charTyped(text);
        return true;
    }
}
