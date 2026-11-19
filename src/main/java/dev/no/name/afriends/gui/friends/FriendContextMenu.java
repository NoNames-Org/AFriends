package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.core.Friend;
import dev.no.name.afriends.gui.render.Colors;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.icon.Icon;
import dev.no.name.afriends.gui.render.text.TextStyle;
import dev.no.name.afriends.gui.sound.UiSound;
import dev.no.name.afriends.gui.sound.UiSounds;
import dev.no.name.afriends.gui.Theme;
import net.minecraft.client.Minecraft;
import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.compat.WidgetBase;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class FriendContextMenu extends WidgetBase {
    public static final int WIDTH = 132;
    private static final int PADDING = 4;
    private static final int HEADER_HEIGHT = 16;
    private static final int ITEM_HEIGHT = 18;
    private static final int GAP = 6;
    private static final int DANGER = 0xFFFF6B6B;

    private final FriendsState state;
    private final List<Item> items;
    private final int panelX;

    public record Item(Icon icon, Function<Friend, String> label, Consumer<Friend> action,
                       Predicate<Friend> danger, Predicate<Friend> visible) {
        public Item(Icon icon, Function<Friend, String> label, Consumer<Friend> action,
                    Predicate<Friend> danger) {
            this(icon, label, action, danger, friend -> true);
        }
    }

    public FriendContextMenu(int panelX, FriendsState state, List<Item> items) {
        super(panelX - GAP - WIDTH, 0, WIDTH, HEADER_HEIGHT + items.size() * ITEM_HEIGHT + PADDING * 2,
                Component.literal("Меню друга"));
        this.panelX = panelX;
        this.state = state;
        this.items = List.copyOf(items);
    }

    @Override
    protected void draw(Gfx g, int mouseX, int mouseY, float partialTick) {
        Friend friend = state.menuFriend();
        if (friend == null || state.progress() < 0.99f) {
            return;
        }
        List<Item> shownItems = shown(friend);
        setHeight(HEADER_HEIGHT + shownItems.size() * ITEM_HEIGHT + PADDING * 2);
        Draw.setPixelScale(Minecraft.getInstance().getWindow().getGuiScale());
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        setY(Math.max(4, Math.min(state.menuAnchorY(), screenHeight - getHeight() - 22)));
        setX(panelX - GAP - WIDTH);

        float shown = state.menuProgress();
        Draw.pushAlpha(shown);
        Draw.pushOffset(g, (1 - shown) * 6, 0);
        Draw.shadow(g, getX(), getY() + 2, WIDTH, getHeight(), Theme.RADIUS + 2, 12, Theme.shadow());
        Draw.box(g, getX(), getY(), WIDTH, getHeight(), Theme.RADIUS + 2, Theme.popup(), Theme.borderHover());
        Draw.text(g, Draw.trim(friend.name(), WIDTH - PADDING * 4), getX() + PADDING * 2, getY() + PADDING + 4,
                Theme.textDim(), TextStyle.SMALL);

        for (int i = 0; i < shownItems.size(); i++) {
            drawItem(g, shownItems.get(i), friend, itemY(i), mouseX, mouseY);
        }
        Draw.popOffset(g);
        Draw.popAlpha();
    }

    // пункты, которые этому другу показываем
    private List<Item> shown(Friend friend) {
        return items.stream().filter(item -> item.visible().test(friend)).toList();
    }

    private void drawItem(Gfx g, Item item, Friend friend, int itemY, int mouseX, int mouseY) {
        int itemX = getX() + PADDING;
        int itemWidth = WIDTH - PADDING * 2;
        boolean hovered = mouseX >= itemX && mouseX < itemX + itemWidth && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
        if (hovered) {
            Draw.roundedRect(g, itemX, itemY, itemWidth, ITEM_HEIGHT, Theme.RADIUS, Theme.cardHover());
        }
        int color = item.danger().test(friend) ? DANGER : Colors.mix(Theme.textSoft(), Theme.text(), hovered ? 1 : 0);
        Draw.icon(g, item.icon(), itemX + 6, itemY + (ITEM_HEIGHT - Theme.ICON_SMALL) / 2f, Theme.ICON_SMALL, color);
        Draw.text(g, item.label().apply(friend), itemX + 6 + Theme.ICON_SMALL + 6, itemY + (ITEM_HEIGHT - 8) / 2f, color);
    }

    private int itemY(int index) {
        return getY() + PADDING + HEADER_HEIGHT + index * ITEM_HEIGHT;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return state.menuFriend() != null && super.isMouseOver(mouseX, mouseY);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        Friend friend = state.menuFriend();
        if (friend == null) {
            return;
        }
        List<Item> shownItems = shown(friend);
        for (int i = 0; i < shownItems.size(); i++) {
            int itemY = itemY(i);
            if (event.y() >= itemY && event.y() < itemY + ITEM_HEIGHT) {
                UiSounds.play(UiSound.CLICK);
                state.closeMenu();
                shownItems.get(i).action().accept(friend);
                return;
            }
        }
    }

    @Override
    public boolean shouldTakeFocusAfterInteraction() {
        return false;
    }
}
