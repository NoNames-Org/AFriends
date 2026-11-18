package dev.no.name.afriends.gui.friends;

import com.mojang.blaze3d.platform.InputConstants;
import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.core.GroupChats;
import dev.no.name.afriends.core.GroupPermissions;
import dev.no.name.afriends.core.MessageRoute;
import dev.no.name.afriends.core.PrivateMessages;
import dev.no.name.afriends.core.ProfileRoute;
import dev.no.name.afriends.gui.media.MediaFiles;
import dev.no.name.afriends.gui.render.icon.Icon;
import dev.no.name.afriends.gui.sound.UiSound;
import dev.no.name.afriends.gui.sound.UiSounds;
import dev.no.name.afriends.gui.widget.ConfirmBox;
import dev.no.name.afriends.gui.widget.TextFieldWidget;
import dev.no.name.afriends.gui.widget.Widget;

import java.util.ArrayList;
import java.util.List;

/**
 * Ряд кнопок под шапкой профиля. У игрока — «Написать» и блокировка (только с игроком сети). У группы — то,
 * на что есть права: название, описание (правятся тут же, в поле на месте кнопок), аватарка, позвать друга
 */
final class ProfileActions {
    static final int MAX_DESCRIPTION = 200;
    private static final int FIELD = 16;
    private static final int SAVE_WIDTH = 60;

    private enum Edit { NONE, TITLE, DESCRIPTION }

    private record Action(String label, boolean accent, boolean danger, Runnable run) {
    }

    private final MessageRoute route;
    private final PrivateMessages messages;
    private final ProfilePanel.Host host;
    private final TextFieldWidget editField = new TextFieldWidget(Icon.EDIT, "");
    private String chat;
    private boolean group;
    private Edit edit = Edit.NONE;
    private float x;
    private float y;
    private float width;

    ProfileActions(MessageRoute route, PrivateMessages messages, ProfilePanel.Host host) {
        this.route = route;
        this.messages = messages;
        this.host = host;
    }

    void reset(String chat) {
        this.chat = chat;
        this.group = PrivateMessages.isGroup(chat);
        blur();
    }

    void blur() {
        edit = Edit.NONE;
        editField.blur();
    }

    boolean isEditing() {
        return edit != Edit.NONE;
    }

    // ряд занимает место, только если в нём что-то есть
    boolean isVisible() {
        return isEditing() || !actions().isEmpty();
    }

    void layout(float x, float y, float width) {
        this.x = x;
        this.y = y;
        this.width = width;
        editField.setBounds((int) x, (int) y, (int) width - SAVE_WIDTH - 4, FIELD);
    }

    private List<Action> actions() {
        List<Action> list = new ArrayList<>();
        if (!group) {
            list.add(new Action("Написать", true, false, host::write));
            String name = messages.displayName(chat);
            ProfileRoute profile = route.profile();
            if (profile != null && profile.available() && route.supportsMedia(chat)) {
                boolean blocked = profile.isBlocked(name);
                list.add(new Action(blocked ? "Разблокировать" : "Заблокировать", false, !blocked, () -> confirmBlock(name, !blocked)));
            }
            return list;
        }
        GroupChats groups = route.groups();
        if (groups == null || !groups.available()) {
            return list;
        }
        int perms = groups.myPerms(chat);
        if (GroupPermissions.has(perms, GroupPermissions.TITLE)) {
            list.add(new Action("Название", false, false, () -> startEdit(Edit.TITLE)));
        }
        if (GroupPermissions.has(perms, GroupPermissions.DESCRIPTION)) {
            list.add(new Action("Описание", false, false, () -> startEdit(Edit.DESCRIPTION)));
        }
        if (GroupPermissions.has(perms, GroupPermissions.AVATAR)) {
            list.add(new Action("Аватарка", false, false, this::chooseAvatar));
        }
        if (GroupPermissions.has(perms, GroupPermissions.ADD)) {
            list.add(new Action("Позвать друга", false, false, () -> host.invite(chat)));
        }
        return list;
    }

    private float saveX() {
        return x + width - SAVE_WIDTH;
    }

    void draw(Gfx g, int mouseX, int mouseY) {
        if (isEditing()) {
            editField.render(g, mouseX, mouseY);
            PanelKit.button(g, "Сохранить", saveX(), y, SAVE_WIDTH, true, false, false, mouseX, mouseY);
            return;
        }
        float buttonX = x;
        for (Action action : actions()) {
            float buttonWidth = PanelKit.buttonWidth(action.label());
            PanelKit.button(g, action.label(), buttonX, y, buttonWidth, action.accent(), action.danger(), false, mouseX, mouseY);
            buttonX += buttonWidth + 4;
        }
    }

    // true — клик по ряду; клик мимо поля правки отменяет правку
    boolean click(double mouseX, double mouseY) {
        if (isEditing()) {
            if (PanelKit.inside(mouseX, mouseY, saveX(), y, SAVE_WIDTH, PanelKit.BUTTON)) {
                save();
                return true;
            }
            if (editField.mouseClicked(mouseX, mouseY, Widget.LEFT_BUTTON)) {
                return true;
            }
            blur();
            return false;
        }
        float buttonX = x;
        for (Action action : actions()) {
            float buttonWidth = PanelKit.buttonWidth(action.label());
            if (PanelKit.inside(mouseX, mouseY, buttonX, y, buttonWidth, PanelKit.BUTTON)) {
                UiSounds.play(UiSound.CLICK);
                action.run().run();
                return true;
            }
            buttonX += buttonWidth + 4;
        }
        return false;
    }

    // только пока идёт правка; Esc — отменить, Enter — сохранить
    boolean keyPressed(int key) {
        if (!isEditing()) {
            return false;
        }
        if (key == InputConstants.KEY_ESCAPE) {
            blur();
        } else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            save();
        } else {
            editField.keyPressed(key);
        }
        return true;
    }

    boolean charTyped(String text) {
        if (!isEditing()) {
            return false;
        }
        editField.charTyped(text);
        return true;
    }

    private void confirmBlock(String name, boolean block) {
        String text = block ? "Заблокировать " + name + "? Он не сможет писать тебе через сеть AFriends."
                : "Разблокировать " + name + "? Он снова сможет тебе писать.";
        host.confirm(new ConfirmBox.Request(text, null, false, block ? "Заблокировать" : "Разблокировать", block,
                checked -> route.profile().setBlocked(name, block, host::warn)));
    }

    private void startEdit(Edit kind) {
        edit = kind;
        editField.withMaxLength(kind == Edit.TITLE ? GroupChats.MAX_TITLE : MAX_DESCRIPTION);
        editField.setText(kind == Edit.TITLE ? messages.displayName(chat) : messages.description(chat));
        editField.focus();
    }

    private void save() {
        String text = editField.getText().replaceAll("\\s+", " ").strip();
        GroupChats groups = route.groups();
        if (groups != null && edit == Edit.TITLE && !text.isEmpty()) {
            groups.rename(chat, text, host::warn);
        } else if (groups != null && edit == Edit.DESCRIPTION) {
            groups.setDescription(chat, text, host::warn);
        } else {
            return;
        }
        UiSounds.play(UiSound.CLICK);
        blur();
    }

    private void chooseAvatar() {
        String target = chat;
        MediaFiles.chooseImage(file -> {
            GroupChats groups = route.groups();
            if (groups != null) {
                host.info("Аватарка загружается…");
                groups.setAvatar(target, file, host::warn);
            }
        });
    }
}
