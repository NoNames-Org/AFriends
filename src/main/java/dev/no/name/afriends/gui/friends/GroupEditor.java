package dev.no.name.afriends.gui.friends;

import com.mojang.blaze3d.platform.InputConstants;
import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.core.GroupChats;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.icon.Icon;
import dev.no.name.afriends.gui.render.text.TextStyle;
import dev.no.name.afriends.gui.sound.UiSound;
import dev.no.name.afriends.gui.sound.UiSounds;
import dev.no.name.afriends.gui.widget.TextFieldWidget;
import dev.no.name.afriends.gui.widget.Widget;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Окно группы посреди переписки. Четыре вида: новая группа (название и галочки у друзей), позвать друга,
 * участники (владелец может исключить, любой — выйти) и переименование. Клик мимо или Esc — закрыть
 */
final class GroupEditor {
    private static final int WIDTH = 190;
    private static final int PAD = 6;
    private static final int TITLE = 14;
    private static final int FIELD = 16;
    private static final int ROW = 16;
    private static final int VISIBLE = 7;
    private static final int BUTTON = 16;

    enum Mode { CREATE, ADD, MEMBERS, RENAME }

    // что окно просит сделать; чат — ключ группы
    interface Host {
        void create(List<String> friends, String title);

        void add(String chat, String friend);

        void kick(String chat, String member);

        void leave(String chat);

        void rename(String chat, String title);
    }

    private final Host host;
    private final TextFieldWidget title = new TextFieldWidget(Icon.EDIT, "Название группы").withMaxLength(GroupChats.MAX_TITLE);
    private final Set<String> picked = new LinkedHashSet<>();
    private Mode mode;
    private String chat;
    private boolean owned;
    private List<String> names = List.of();
    private int scroll;
    private float x;
    private float y;
    private float height;

    GroupEditor(Host host) {
        this.host = host;
    }

    boolean isOpen() {
        return mode != null;
    }

    void openCreate(List<String> friends, int areaWidth, int areaHeight) {
        open(Mode.CREATE, null, friends, false, "", areaWidth, areaHeight);
    }

    void openAdd(String chat, List<String> friends, int areaWidth, int areaHeight) {
        open(Mode.ADD, chat, friends, false, "", areaWidth, areaHeight);
    }

    void openMembers(String chat, List<String> members, boolean owned, int areaWidth, int areaHeight) {
        open(Mode.MEMBERS, chat, members, owned, "", areaWidth, areaHeight);
    }

    void openRename(String chat, String current, int areaWidth, int areaHeight) {
        open(Mode.RENAME, chat, List.of(), false, current, areaWidth, areaHeight);
    }

    private void open(Mode mode, String chat, List<String> names, boolean owned, String text, int areaWidth, int areaHeight) {
        this.mode = mode;
        this.chat = chat;
        this.names = List.copyOf(names);
        this.owned = owned;
        this.scroll = 0;
        picked.clear();
        title.setText(text);
        int rows = mode == Mode.RENAME ? 0 : Math.min(VISIBLE, Math.max(1, names.size()));
        boolean field = mode == Mode.CREATE || mode == Mode.RENAME;
        boolean button = mode != Mode.ADD;
        height = PAD * 2 + TITLE + (field ? FIELD + 6 : 0) + rows * ROW + (button ? BUTTON + 6 : 0);
        x = (areaWidth - WIDTH) / 2f;
        y = (areaHeight - height) / 2f;
        title.setBounds((int) x + PAD, (int) y + PAD + TITLE, WIDTH - PAD * 2, FIELD);
        if (field) {
            title.focus();
        } else {
            title.blur();
        }
    }

    void close() {
        mode = null;
        title.blur();
    }

    private String heading() {
        return switch (mode) {
            case CREATE -> "Новая группа";
            case ADD -> "Позвать в группу";
            case MEMBERS -> "Участники";
            case RENAME -> "Название группы";
        };
    }

    private float listTop() {
        return y + PAD + TITLE + (mode == Mode.CREATE ? FIELD + 6 : 0);
    }

    private float buttonY() {
        return y + height - PAD - BUTTON;
    }

    private String buttonLabel() {
        return switch (mode) {
            case CREATE -> picked.isEmpty() ? "Отметь друзей" : "Создать (" + (picked.size() + 1) + ")";
            case MEMBERS -> "Выйти из группы";
            case RENAME -> "Сохранить";
            case ADD -> "";
        };
    }

    void draw(Gfx g, int mouseX, int mouseY) {
        if (mode == null) {
            return;
        }
        Draw.shadow(g, x, y + 2, WIDTH, height, Theme.RADIUS + 2, 12, Theme.shadow());
        Draw.box(g, x, y, WIDTH, height, Theme.RADIUS + 2, Theme.popup(), Theme.border());
        Draw.text(g, heading(), x + PAD, y + PAD, Theme.textSoft(), TextStyle.SMALL);
        if (mode == Mode.CREATE || mode == Mode.RENAME) {
            title.render(g, mouseX, mouseY);
        }
        if (mode != Mode.RENAME) {
            drawList(g, mouseX, mouseY);
        }
        if (mode != Mode.ADD) {
            drawButton(g, mouseX, mouseY);
        }
    }

    private void drawList(Gfx g, int mouseX, int mouseY) {
        float top = listTop();
        if (names.isEmpty()) {
            String empty = mode == Mode.MEMBERS ? "Кроме тебя никого" : "Нет друзей по сети, кого позвать";
            Draw.textCentered(g, empty, x + WIDTH / 2f, top + 4, Theme.textDim());
            return;
        }
        for (int i = scroll; i < names.size() && i < scroll + VISIBLE; i++) {
            float rowY = top + (i - scroll) * ROW;
            String name = names.get(i);
            boolean hover = mouseX >= x + PAD && mouseX < x + WIDTH - PAD && mouseY >= rowY && mouseY < rowY + ROW;
            if (hover && mode != Mode.MEMBERS) {
                Draw.roundedRect(g, x + PAD - 2, rowY, WIDTH - PAD * 2 + 4, ROW, Theme.RADIUS, Theme.cardHover());
            }
            float textX = x + PAD + 2;
            if (mode == Mode.CREATE) {
                boolean on = picked.contains(name);
                Draw.box(g, textX, rowY + 4, 8, 8, 2, on ? Theme.accent() : Theme.card(), on ? Theme.accent() : Theme.borderHover());
                if (on) {
                    Draw.icon(g, Icon.CHECK, textX + 1, rowY + 5, 6, Theme.accentText());
                }
                textX += 13;
            }
            Draw.text(g, Draw.trim(name, (int) (x + WIDTH - PAD - textX - 40)), textX, rowY + 4, Theme.text());
            if (mode == Mode.MEMBERS && owned) {
                String kick = "Исключить";
                float kickX = x + WIDTH - PAD - Draw.width(kick, TextStyle.SMALL);
                boolean kickHover = mouseX >= kickX && mouseX < x + WIDTH - PAD && mouseY >= rowY && mouseY < rowY + ROW;
                Draw.text(g, kick, kickX, rowY + 5, kickHover ? Theme.DANGER : Theme.textDim(), TextStyle.SMALL);
            }
        }
    }

    private void drawButton(Gfx g, int mouseX, int mouseY) {
        float buttonY = buttonY();
        boolean enabled = mode != Mode.CREATE || !picked.isEmpty();
        boolean hover = enabled && mouseX >= x + PAD && mouseX < x + WIDTH - PAD && mouseY >= buttonY && mouseY < buttonY + BUTTON;
        boolean danger = mode == Mode.MEMBERS;
        int fill = danger ? (hover ? Theme.cardPressed() : Theme.card()) : enabled ? Theme.accent() : Theme.card();
        Draw.roundedRect(g, x + PAD, buttonY, WIDTH - PAD * 2, BUTTON, Theme.RADIUS, fill);
        int color = danger ? Theme.DANGER : enabled ? Theme.accentText() : Theme.textDim();
        Draw.textCentered(g, buttonLabel(), x + WIDTH / 2f, buttonY + 4, color);
    }

    // true — клик внутри окна (обработан); мимо окна оно закрывается
    boolean click(double mouseX, double mouseY) {
        if (mode == null) {
            return false;
        }
        if (mouseX < x || mouseX >= x + WIDTH || mouseY < y || mouseY >= y + height) {
            close();
            return true;
        }
        if ((mode == Mode.CREATE || mode == Mode.RENAME) && title.mouseClicked(mouseX, mouseY, Widget.LEFT_BUTTON)) {
            return true;
        }
        if (mode != Mode.ADD && mouseY >= buttonY() && mouseY < buttonY() + BUTTON) {
            submit();
            return true;
        }
        if (mode == Mode.RENAME) {
            return true;
        }
        int index = scroll + (int) Math.floor((mouseY - listTop()) / ROW);
        if (mouseY < listTop() || index < 0 || index >= names.size() || index >= scroll + VISIBLE) {
            return true;
        }
        String name = names.get(index);
        switch (mode) {
            case CREATE -> {
                if (!picked.remove(name)) {
                    picked.add(name);
                }
                UiSounds.play(UiSound.SELECT);
            }
            case ADD -> {
                host.add(chat, name);
                close();
            }
            case MEMBERS -> {
                float kickX = x + WIDTH - PAD - Draw.width("Исключить", TextStyle.SMALL);
                if (owned && mouseX >= kickX) {
                    host.kick(chat, name);
                    close();
                }
            }
            default -> {
            }
        }
        return true;
    }

    private void submit() {
        String text = title.getText().replaceAll("\\s+", " ").strip();
        switch (mode) {
            case CREATE -> {
                if (picked.isEmpty()) {
                    return;
                }
                host.create(new ArrayList<>(picked), text.isEmpty() ? defaultTitle() : text);
            }
            case RENAME -> {
                if (text.isEmpty()) {
                    return;
                }
                host.rename(chat, text);
            }
            case MEMBERS -> host.leave(chat);
            default -> {
                return;
            }
        }
        UiSounds.play(UiSound.CLICK);
        close();
    }

    // без названия группа зовётся по первым участникам
    private String defaultTitle() {
        String joined = String.join(", ", picked);
        return joined.length() > GroupChats.MAX_TITLE ? joined.substring(0, GroupChats.MAX_TITLE - 1) + "…" : joined;
    }

    boolean keyPressed(int key) {
        if (mode == null) {
            return false;
        }
        if (key == InputConstants.KEY_ESCAPE) {
            close();
            return true;
        }
        if ((key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) && mode != Mode.ADD) {
            submit();
            return true;
        }
        title.keyPressed(key);
        return true;
    }

    boolean charTyped(String text) {
        if (mode == null) {
            return false;
        }
        if (mode == Mode.CREATE || mode == Mode.RENAME) {
            title.charTyped(text);
        }
        return true;
    }

    boolean scroll(double mouseX, double mouseY, double amount) {
        if (mode == null || mouseX < x || mouseX >= x + WIDTH || mouseY < y || mouseY >= y + height) {
            return false;
        }
        scroll = Math.max(0, Math.min(Math.max(0, names.size() - VISIBLE), scroll + (amount < 0 ? 1 : -1)));
        return true;
    }
}
