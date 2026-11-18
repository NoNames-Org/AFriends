package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.core.GroupChats;
import dev.no.name.afriends.core.GroupPermissions;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.icon.Icon;
import dev.no.name.afriends.gui.render.text.TextStyle;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Вкладка «Участники» в профиле группы: голова, ник, префикс плашкой и «владелец». Сверху — ты сам.
 * Владельцу клик по строке открывает права, участнику с правом исключать — «Исключить» справа
 */
final class ProfileMembers {
    private static final int ROW = 18;
    private static final int FACE = 14;
    private static final int SCROLL_STEP = 16;
    private static final String KICK = "Исключить";

    /** Клик по строке: kick — по «Исключить», иначе — по самой строке. */
    record Click(String member, boolean self, boolean kick) {
    }

    private List<String> rows = List.of();
    private String selfName = "";
    private boolean owned;
    private boolean canKick;
    private String owner;
    private float scroll;
    private float x;
    private float y;
    private float width;
    private float height;

    void reset() {
        scroll = 0;
    }

    // состав меняется, пока окно открыто (исключили, позвали): собирается каждый кадр из снимка сети
    void load(GroupChats groups, String chat, String selfName) {
        this.selfName = selfName;
        List<String> all = new ArrayList<>();
        all.add(selfName);
        if (groups != null) {
            groups.members(chat).stream().filter(name -> !name.equalsIgnoreCase(selfName)).forEach(all::add);
            owned = groups.owned(chat);
            owner = owned ? selfName : groups.ownerName(chat);
            canKick = GroupPermissions.has(groups.myPerms(chat), GroupPermissions.KICK);
        }
        rows = List.copyOf(all);
    }

    void layout(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    private boolean isOwner(String name) {
        return owner != null && owner.equalsIgnoreCase(name);
    }

    private boolean kickable(String name) {
        return !owned && canKick && !name.equalsIgnoreCase(selfName) && !isOwner(name);
    }

    void draw(Gfx g, GroupChats groups, String chat, Function<String, PlayerSkin> skins, int mouseX, int mouseY) {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() * ROW - height)));
        g.enableScissor((int) x, (int) y, (int) (x + width), (int) (y + height));
        for (int i = 0; i < rows.size(); i++) {
            float rowY = y + i * ROW - scroll;
            if (rowY + ROW < y || rowY > y + height) {
                continue;
            }
            drawRow(g, groups, chat, rows.get(i), rowY, skins, mouseX, mouseY);
        }
        g.disableScissor();
    }

    private void drawRow(Gfx g, GroupChats groups, String chat, String name, float rowY, Function<String, PlayerSkin> skins,
                         int mouseX, int mouseY) {
        boolean self = name.equalsIgnoreCase(selfName);
        boolean hover = PanelKit.inside(mouseX, mouseY, x, rowY, width, ROW) && mouseY >= y && mouseY < y + height;
        if (hover && owned) {
            Draw.roundedRect(g, x, rowY, width, ROW, Theme.RADIUS, Theme.CARD_HOVER);
        }
        PanelKit.face(g, skins.apply(name), x + 3, rowY + (ROW - FACE) / 2f, FACE);
        float textX = x + 3 + FACE + 6;
        float right = x + width - 4;
        if (kickable(name)) {
            float kickX = right - Draw.width(KICK, TextStyle.SMALL);
            boolean kickHover = hover && mouseX >= kickX;
            Draw.text(g, KICK, kickX, rowY + 6, kickHover ? Theme.DANGER : Theme.TEXT_DIM, TextStyle.SMALL);
            right = kickX - 4;
        } else if (owned) {
            Draw.icon(g, Icon.CHEVRON_RIGHT, right - Theme.ICON_SMALL, rowY + (ROW - Theme.ICON_SMALL) / 2f, Theme.ICON_SMALL,
                    hover ? Theme.TEXT : Theme.TEXT_FAINT);
            right -= Theme.ICON_SMALL + 4;
        }
        String prefix = groups == null ? "" : groups.prefixOf(chat, name);
        String label = self ? name + " (ты)" : name;
        float tags = (prefix == null || prefix.isEmpty() ? 0 : PanelKit.tagWidth(prefix) + 3)
                + (isOwner(name) ? PanelKit.tagWidth("владелец") + 3 : 0);
        String shown = Draw.trim(label, (int) Math.max(20, right - textX - tags));
        Draw.text(g, shown, textX, rowY + 5, Theme.TEXT);
        float tagX = textX + Draw.width(shown) + 4;
        if (prefix != null && !prefix.isEmpty()) {
            tagX += PanelKit.tag(g, prefix, tagX, rowY + 4.5f, Theme.TEXT_SOFT) + 3;
        }
        if (isOwner(name)) {
            PanelKit.tag(g, "владелец", tagX, rowY + 4.5f, Theme.ACCENT);
        }
    }

    // null — мимо строк или клик ничего не делает (не владелец и нельзя исключить)
    Click click(double mouseX, double mouseY) {
        if (!PanelKit.inside(mouseX, mouseY, x, y, width, height)) {
            return null;
        }
        int index = (int) Math.floor((mouseY - y + scroll) / ROW);
        if (index < 0 || index >= rows.size()) {
            return null;
        }
        String name = rows.get(index);
        boolean self = name.equalsIgnoreCase(selfName);
        if (owned) {
            return new Click(name, self, false);
        }
        if (kickable(name) && mouseX >= x + width - 4 - Draw.width(KICK, TextStyle.SMALL)) {
            return new Click(name, false, true);
        }
        return null;
    }

    void scroll(double amount) {
        scroll = Math.max(0, scroll - (float) (amount * SCROLL_STEP));
    }
}
