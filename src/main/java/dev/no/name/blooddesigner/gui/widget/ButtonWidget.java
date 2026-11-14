package dev.no.name.blooddesigner.gui.widget;

import dev.no.name.blooddesigner.gui.animation.Animated;
import dev.no.name.blooddesigner.gui.animation.Motion;
import dev.no.name.blooddesigner.gui.render.Colors;
import dev.no.name.blooddesigner.gui.render.Draw;
import dev.no.name.blooddesigner.gui.render.icon.Icon;
import dev.no.name.blooddesigner.gui.sound.UiSound;
import dev.no.name.blooddesigner.gui.sound.UiSounds;
import dev.no.name.blooddesigner.gui.Theme;
import dev.no.name.blooddesigner.compat.Gfx;

public class ButtonWidget extends Widget {
    private static final int ICON_GAP = 4;

    private final Icon icon;
    private final String text;
    private final Runnable onClick;
    private final Animated hover = new Animated(0, Motion.FAST);

    private final Animated press = new Animated(0, Motion.SLOW);
    private UiSound clickSound = UiSound.CLICK;

    public ButtonWidget(Icon icon, String text, Runnable onClick) {
        this.icon = icon;
        this.text = text;
        this.onClick = onClick;
    }

    public ButtonWidget(String text, Runnable onClick) {
        this(null, text, onClick);
    }

    public ButtonWidget(Icon icon, Runnable onClick) {
        this(icon, null, onClick);
    }

    @Override
    public void render(Gfx g, int mouseX, int mouseY) {
        hover.setTarget(contains(mouseX, mouseY));
        float hovered = hover.get();
        float pressed = press.get();

        int fill = Colors.mix(Colors.mix(Theme.CARD, Theme.CARD_HOVER, hovered), Theme.CARD_PRESSED, pressed);
        int border = Colors.mix(Theme.BORDER, Theme.BORDER_HOVER, Math.max(hovered, pressed));
        Draw.box(g, x, y, width, height, Theme.RADIUS, fill, border);

        int iconWidth = icon == null ? 0 : Theme.ICON_SMALL;
        int textWidth = text == null ? 0 : Draw.width(text);
        int gap = icon != null && text != null ? ICON_GAP : 0;
        float contentX = x + (width - iconWidth - gap - textWidth) / 2f;

        int color = Colors.mix(Theme.TEXT_DIM, Theme.TEXT, text != null ? 0.75f + 0.25f * hovered : hovered);
        if (icon != null) {
            Draw.icon(g, icon, contentX, y + (height - Theme.ICON_SMALL) / 2f, Theme.ICON_SMALL, color);
        }
        if (text != null) {
            Draw.text(g, text, contentX + iconWidth + gap, y + (height - 8) / 2, color);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == LEFT_BUTTON && contains(mouseX, mouseY)) {
            press.snap(1);
            press.setTarget(0);
            UiSounds.play(clickSound);
            onClick.run();
            return true;
        }
        return false;
    }
}
