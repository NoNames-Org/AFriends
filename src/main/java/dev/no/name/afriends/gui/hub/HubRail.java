package dev.no.name.afriends.gui.hub;

import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.compat.WidgetBase;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.animation.Animated;
import dev.no.name.afriends.gui.animation.Motion;
import dev.no.name.afriends.gui.render.Colors;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.icon.Icon;
import dev.no.name.afriends.gui.render.text.TextStyle;
import dev.no.name.afriends.gui.sound.UiSound;
import dev.no.name.afriends.gui.sound.UiSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class HubRail extends WidgetBase {
    public static final int WIDTH = 24;
    private static final int BUTTON = 18;
    private static final int PAD = 3;
    private static final int STEP = BUTTON + 4;
    private static final int ICON = 10;
    private static final int BADGE = 9;

    public record Button(Icon icon, String label, BooleanSupplier active, Supplier<String> badge, Runnable action) {
    }

    private final List<Button> top;
    private final List<Button> bottom;
    private final Animated[] hover;

    public HubRail(int x, int y, int height, List<Button> top, List<Button> bottom) {
        super(x, y, WIDTH, height, Component.literal("Панель"));
        this.top = List.copyOf(top);
        this.bottom = List.copyOf(bottom);
        this.hover = new Animated[top.size() + bottom.size()];
        for (int i = 0; i < hover.length; i++) {
            hover[i] = new Animated(0, Motion.FAST);
        }
    }

    @Override
    protected void draw(Gfx g, int mouseX, int mouseY, float partialTick) {
        Draw.setPixelScale(Minecraft.getInstance().getWindow().getGuiScale());
        Draw.shadow(g, getX(), getY() + 2, WIDTH, height, Theme.PANEL_RADIUS, 12, Colors.fade(Theme.SHADOW, 0.6f));
        Draw.gradient(g, getX(), getY(), WIDTH, height, Theme.PANEL_RADIUS,
                Colors.fade(Theme.PANEL_TOP, 0.92f), Colors.fade(Theme.PANEL_BOTTOM, 0.92f));
        Draw.border(g, getX(), getY(), WIDTH, height, Theme.PANEL_RADIUS, Theme.BORDER);
        for (int i = 0; i < top.size(); i++) {
            drawButton(g, top.get(i), buttonY(i, false), hover[i], mouseX, mouseY);
        }
        for (int i = 0; i < bottom.size(); i++) {
            drawButton(g, bottom.get(i), buttonY(i, true), hover[top.size() + i], mouseX, mouseY);
        }
    }

    private int buttonY(int index, boolean fromBottom) {
        return fromBottom ? getY() + height - PAD - BUTTON - index * STEP : getY() + PAD + index * STEP;
    }

    private void drawButton(Gfx g, Button button, int y, Animated hoverAnim, int mouseX, int mouseY) {
        int x = getX() + PAD;
        boolean hovered = mouseX >= x && mouseX < x + BUTTON && mouseY >= y && mouseY < y + BUTTON;
        hoverAnim.setTarget(hovered);
        boolean active = button.active().getAsBoolean();
        float hoverAmount = hoverAnim.get();
        int fill = active ? Theme.ACCENT : Colors.mix(Colors.fade(Theme.CARD, 0), Theme.CARD_HOVER, hoverAmount);
        int border = active ? Theme.ACCENT : Colors.mix(Colors.fade(Theme.BORDER, 0), Theme.BORDER_HOVER, hoverAmount);
        Draw.box(g, x, y, BUTTON, BUTTON, Theme.RADIUS, fill, border);
        int iconColor = active ? Theme.ACCENT_TEXT : Colors.mix(Theme.TEXT_SOFT, Theme.TEXT, hoverAmount);
        Draw.icon(g, button.icon(), x + (BUTTON - ICON) / 2f, y + (BUTTON - ICON) / 2f, ICON, iconColor);

        String badge = button.badge().get();
        if (badge != null) {
            float badgeWidth = Math.max(BADGE, Draw.width(badge, TextStyle.SMALL) + 4);
            float badgeX = x + BUTTON - badgeWidth + 3;
            float badgeY = y - 3;
            int badgeFill = active ? Theme.ACCENT_TEXT : Theme.ACCENT;
            int badgeText = active ? Theme.ACCENT : Theme.ACCENT_TEXT;
            Draw.roundedRect(g, badgeX, badgeY, badgeWidth, BADGE, BADGE / 2f, badgeFill);
            Draw.text(g, badge, badgeX + (badgeWidth - Draw.width(badge, TextStyle.SMALL)) / 2, badgeY + 1.5f, badgeText,
                    TextStyle.SMALL);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        Button hit = buttonAt(event.y());
        if (hit != null) {
            UiSounds.play(UiSound.CLICK);
            hit.action().run();
        }
    }

    private Button buttonAt(double mouseY) {
        for (int i = 0; i < top.size(); i++) {
            int y = buttonY(i, false);
            if (mouseY >= y && mouseY < y + BUTTON) {
                return top.get(i);
            }
        }
        for (int i = 0; i < bottom.size(); i++) {
            int y = buttonY(i, true);
            if (mouseY >= y && mouseY < y + BUTTON) {
                return bottom.get(i);
            }
        }
        return null;
    }

    @Override
    public boolean shouldTakeFocusAfterInteraction() {
        return false;
    }
}
