package dev.no.name.blooddesigner.compat;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public abstract class WidgetBase extends AbstractWidget {
    protected WidgetBase(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    protected abstract void draw(Gfx g, int mouseX, int mouseY, float partialTick);

    @Override
    protected final void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        draw(new Gfx(graphics), mouseX, mouseY, partialTick);
    }

    /**
     * Панели мода стоят прямо в окнах игры (в чате). Стрелки и Tab на них не переводят: иначе «вверх» в чате
     * сначала уводит фокус со строки ввода на панель, и вернувшееся из истории сообщение уже не отредактировать
     */
    @Override
    public ComponentPath nextFocusPath(FocusNavigationEvent event) {
        return null;
    }

    /** Ждёт ли виджет ввода с клавиатуры (в нём активно своё поле). Нет — после клика фокус вернётся строке чата. */
    public boolean wantsTextInput() {
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
