package dev.no.name.blooddesigner.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public abstract class WidgetBase extends AbstractWidget {
    protected WidgetBase(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    protected abstract void draw(Gfx g, int mouseX, int mouseY, float partialTick);

    @Override
    protected final void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        draw(new Gfx(graphics), mouseX, mouseY, partialTick);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
