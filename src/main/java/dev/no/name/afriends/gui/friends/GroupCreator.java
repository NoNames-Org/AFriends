package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.gui.media.ChatMedia;
import com.mojang.blaze3d.platform.InputConstants;
import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.core.GroupChats;
import dev.no.name.afriends.core.GroupPermissions;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.media.MediaFiles;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.icon.Icon;
import dev.no.name.afriends.gui.render.text.TextStyle;
import dev.no.name.afriends.gui.sound.UiSound;
import dev.no.name.afriends.gui.sound.UiSounds;
import dev.no.name.afriends.gui.widget.TextFieldWidget;
import dev.no.name.afriends.gui.widget.Widget;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Новая группа, как в Telegram, в два шага. Первый — круглое фото (по желанию) и название, «Далее».
 * Второй — друзья по сети галочками с поиском, «Создать». Фото уходит, когда группа появится в снимке сети
 */
final class GroupCreator {
    private static final int WIDTH = 220;
    private static final int PAD = 8;
    private static final int TITLE = 14;
    private static final int FIELD = 16;
    private static final int PHOTO = 48;
    private static final int ROW = 16;
    private static final int VISIBLE = 8;
    private static final int AVATAR_ATTEMPTS = 12;
    private static final long AVATAR_DELAY_MS = 500;

    private final TextFieldWidget title = new TextFieldWidget(Icon.EDIT, "Название группы").withMaxLength(GroupChats.MAX_TITLE);
    private final TextFieldWidget search = new TextFieldWidget(Icon.SEARCH, "Поиск друзей").withMaxLength(16);
    private final Set<String> picked = new LinkedHashSet<>();
    // done получает ключ новой группы и её название
    private final BiConsumer<String, String> done;
    private final Consumer<String> warn;
    private final ChatMedia chatMedia;
    private GroupChats groups;
    private List<String> friends = List.of();
    private Path photo;
    private int step;
    private int scroll;
    private float x;
    private float y;
    private float height;
    private int areaWidth;
    private int areaHeight;

    GroupCreator(ChatMedia chatMedia, BiConsumer<String, String> done, Consumer<String> warn) {
        this.chatMedia = chatMedia;
        this.done = done;
        this.warn = warn;
    }

    boolean isOpen() {
        return step > 0;
    }

    void open(GroupChats groups, int areaWidth, int areaHeight) {
        this.groups = groups;
        this.friends = List.copyOf(groups.friends());
        this.areaWidth = areaWidth;
        this.areaHeight = areaHeight;
        picked.clear();
        photo = null;
        title.setText("");
        search.setText("");
        showStep(1);
    }

    private void showStep(int value) {
        step = value;
        scroll = 0;
        height = step == 1 ? PAD * 2 + TITLE + PHOTO + 8 + FIELD + 8 + PanelKit.BUTTON
                : PAD * 2 + TITLE + FIELD + 6 + VISIBLE * ROW + 6 + PanelKit.BUTTON;
        x = (areaWidth - WIDTH) / 2f;
        y = (areaHeight - height) / 2f;
        title.setBounds((int) x + PAD, (int) (photoY() + PHOTO + 8), WIDTH - PAD * 2, FIELD);
        search.setBounds((int) x + PAD, (int) y + PAD + TITLE, WIDTH - PAD * 2, FIELD);
        if (step == 1) {
            search.blur();
            title.focus();
        } else {
            title.blur();
            search.focus();
        }
    }

    void close() {
        step = 0;
        title.blur();
        search.blur();
    }

    private float photoY() {
        return y + PAD + TITLE;
    }

    private float photoX() {
        return x + (WIDTH - PHOTO) / 2f;
    }

    private float buttonY() {
        return y + height - PAD - PanelKit.BUTTON;
    }

    private float rightX() {
        return x + WIDTH - PAD - 70;
    }

    private float leftX() {
        return rightX() - 4 - 60;
    }

    private float listTop() {
        return y + PAD + TITLE + FIELD + 6;
    }

    private List<String> shown() {
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        return query.isEmpty() ? friends : friends.stream().filter(name -> name.toLowerCase(Locale.ROOT).contains(query)).toList();
    }

    private String cleanTitle() {
        return title.getText().replaceAll("\\s+", " ").strip();
    }

    void draw(Gfx g, int mouseX, int mouseY) {
        if (step == 0) {
            return;
        }
        Draw.roundedRect(g, 0, 0, areaWidth, areaHeight, Theme.PANEL_RADIUS, 0x66000000);
        PanelKit.frame(g, x, y, WIDTH, height);
        Draw.text(g, step == 1 ? "Новая группа" : "Участники · выбрано " + picked.size(), x + PAD, y + PAD, Theme.textSoft(),
                TextStyle.SMALL);
        if (step == 1) {
            drawPhoto(g, mouseX, mouseY);
            title.render(g, mouseX, mouseY);
            PanelKit.button(g, "Отмена", leftX(), buttonY(), 60, false, false, false, mouseX, mouseY);
            PanelKit.button(g, "Далее", rightX(), buttonY(), 70, true, false, cleanTitle().isEmpty(), mouseX, mouseY);
            return;
        }
        search.render(g, mouseX, mouseY);
        drawFriends(g, mouseX, mouseY);
        PanelKit.button(g, "Назад", leftX(), buttonY(), 60, false, false, false, mouseX, mouseY);
        PanelKit.button(g, "Создать", rightX(), buttonY(), 70, true, false, picked.isEmpty(), mouseX, mouseY);
    }

    private void drawPhoto(Gfx g, int mouseX, int mouseY) {
        float photoX = photoX();
        float photoY = photoY();
        boolean hover = PanelKit.inside(mouseX, mouseY, photoX, photoY, PHOTO, PHOTO);
        if (photo != null && PanelKit.filePreview(g, chatMedia, photo, photoX, photoY, PHOTO)) {
            return;
        }
        Draw.roundedRect(g, photoX, photoY, PHOTO, PHOTO, PHOTO / 2f, hover ? PanelKit.GROUP_COLOR : Theme.tileOn());
        float icon = PHOTO * 0.4f;
        Draw.icon(g, photo != null ? Icon.CHECK : Icon.CAMERA, photoX + (PHOTO - icon) / 2, photoY + (PHOTO - icon) / 2 - 3, icon,
                0xFFFFFFFF);
        String label = photo != null ? "Фото выбрано" : "Фото";
        Draw.text(g, label, photoX + (PHOTO - Draw.width(label, TextStyle.SMALL)) / 2f, photoY + PHOTO - 11, 0xFFFFFFFF,
                TextStyle.SMALL);
    }

    private void drawFriends(Gfx g, int mouseX, int mouseY) {
        List<String> shown = shown();
        float top = listTop();
        if (shown.isEmpty()) {
            String empty = friends.isEmpty() ? "Нет друзей по сети, кого позвать" : "Никого не нашлось";
            Draw.textCentered(g, empty, x + WIDTH / 2f, top + 6, Theme.textDim());
            return;
        }
        for (int i = scroll; i < shown.size() && i < scroll + VISIBLE; i++) {
            float rowY = top + (i - scroll) * ROW;
            String name = shown.get(i);
            if (PanelKit.inside(mouseX, mouseY, x + PAD, rowY, WIDTH - PAD * 2, ROW)) {
                Draw.roundedRect(g, x + PAD - 2, rowY, WIDTH - PAD * 2 + 4, ROW, Theme.RADIUS, Theme.cardHover());
            }
            PanelKit.checkbox(g, picked.contains(name), false, x + PAD + 2, rowY + 4);
            Draw.text(g, Draw.trim(name, WIDTH - PAD * 2 - 18), x + PAD + 15, rowY + 4, Theme.text());
        }
    }

    // true — клик поглощён (окно модальное); мимо окна — закрыть
    boolean click(double mouseX, double mouseY) {
        if (step == 0) {
            return false;
        }
        if (!PanelKit.inside(mouseX, mouseY, x, y, WIDTH, height)) {
            close();
            return true;
        }
        boolean onButtons = mouseY >= buttonY() && mouseY < buttonY() + PanelKit.BUTTON;
        boolean left = onButtons && PanelKit.inside(mouseX, mouseY, leftX(), buttonY(), 60, PanelKit.BUTTON);
        boolean right = onButtons && PanelKit.inside(mouseX, mouseY, rightX(), buttonY(), 70, PanelKit.BUTTON);
        if (step == 1) {
            if (left) {
                close();
            } else if (right) {
                next();
            } else if (PanelKit.inside(mouseX, mouseY, photoX(), photoY(), PHOTO, PHOTO)) {
                MediaFiles.chooseImage(file -> photo = file);
                UiSounds.play(UiSound.CLICK);
            } else {
                title.mouseClicked(mouseX, mouseY, Widget.LEFT_BUTTON);
            }
            return true;
        }
        if (left) {
            showStep(1);
        } else if (right) {
            create();
        } else if (!search.mouseClicked(mouseX, mouseY, Widget.LEFT_BUTTON)) {
            toggleAt(mouseY);
        }
        return true;
    }

    private void toggleAt(double mouseY) {
        List<String> shown = shown();
        int index = scroll + (int) Math.floor((mouseY - listTop()) / ROW);
        if (mouseY < listTop() || index < 0 || index >= shown.size() || index >= scroll + VISIBLE) {
            return;
        }
        String name = shown.get(index);
        if (!picked.remove(name)) {
            picked.add(name);
        }
        UiSounds.play(UiSound.SELECT);
    }

    private void next() {
        if (cleanTitle().isEmpty()) {
            return;
        }
        UiSounds.play(UiSound.CLICK);
        showStep(2);
    }

    private void create() {
        if (picked.isEmpty()) {
            return;
        }
        String name = cleanTitle();
        Path file = photo;
        GroupChats target = groups;
        target.create(new ArrayList<>(picked), name, chat -> {
            done.accept(chat, name);
            if (file != null) {
                sendAvatarWhenReady(target, chat, file, 0);
            }
        }, warn);
        UiSounds.play(UiSound.CLICK);
        close();
    }

    // группа видна в снимке сети не сразу: ждём, пока появятся права на аватарку, и только тогда шлём фото
    private void sendAvatarWhenReady(GroupChats target, String chat, Path file, int attempt) {
        if (GroupPermissions.has(target.myPerms(chat), GroupPermissions.AVATAR) || attempt >= AVATAR_ATTEMPTS) {
            target.setAvatar(chat, file, warn);
            return;
        }
        CompletableFuture.delayedExecutor(AVATAR_DELAY_MS, TimeUnit.MILLISECONDS).execute(() ->
                Minecraft.getInstance().execute(() -> sendAvatarWhenReady(target, chat, file, attempt + 1)));
    }

    boolean keyPressed(int key) {
        if (step == 0) {
            return false;
        }
        if (key == InputConstants.KEY_ESCAPE) {
            if (step == 2) {
                showStep(1);
            } else {
                close();
            }
            return true;
        }
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            if (step == 1) {
                next();
            } else {
                create();
            }
            return true;
        }
        (step == 1 ? title : search).keyPressed(key);
        return true;
    }

    boolean charTyped(String text) {
        if (step == 0) {
            return false;
        }
        if ((step == 1 ? title : search).charTyped(text) && step == 2) {
            scroll = 0;
        }
        return true;
    }

    boolean scroll(double mouseX, double mouseY, double amount) {
        if (step != 2 || !PanelKit.inside(mouseX, mouseY, x, y, WIDTH, height)) {
            return step != 0;
        }
        scroll = Math.max(0, Math.min(Math.max(0, shown().size() - VISIBLE), scroll + (amount < 0 ? 1 : -1)));
        return true;
    }
}
