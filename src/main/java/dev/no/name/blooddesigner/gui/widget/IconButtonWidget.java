package dev.no.name.blooddesigner.gui.widget;

import dev.no.name.blooddesigner.compat.Gfx;
import dev.no.name.blooddesigner.gui.Theme;
import dev.no.name.blooddesigner.gui.animation.Animated;
import dev.no.name.blooddesigner.gui.animation.Motion;
import dev.no.name.blooddesigner.gui.render.Colors;
import dev.no.name.blooddesigner.gui.render.Draw;
import dev.no.name.blooddesigner.gui.render.icon.Icon;
import dev.no.name.blooddesigner.gui.sound.UiSound;
import dev.no.name.blooddesigner.gui.sound.UiSounds;

/** Плоская кнопка-значок без рамки: скрепка и значки внутри полосы ввода, как в Telegram */
public class IconButtonWidget extends Widget {
    private final Icon icon;
    private final Runnable onClick;
    private final Animated hover = new Animated(0, Motion.FAST);

    public IconButtonWidget(Icon icon, Runnable onClick) {
        this.icon = icon;
        this.onClick = onClick;
    }

    @Override
    public void render(Gfx g, int mouseX, int mouseY) {
        hover.setTarget(contains(mouseX, mouseY));
        float size = Theme.ICON;
        int color = Colors.mix(Theme.TEXT_DIM, Theme.TEXT, hover.get());
        Draw.icon(g, icon, x + (width - size) / 2f, y + (height - size) / 2f, size, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == LEFT_BUTTON && contains(mouseX, mouseY)) {
            UiSounds.play(UiSound.CLICK);
            onClick.run();
            return true;
        }
        return false;
    }
}
