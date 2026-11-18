package dev.no.name.afriends.compat;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class ScreenBase extends Screen {
    protected ScreenBase(Component title) {
        super(title);
    }

    protected abstract void drawBackground(Gfx g, int mouseX, int mouseY, float partialTick);

    protected abstract void draw(Gfx g, int mouseX, int mouseY, float partialTick);

    @Override
    public final void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(new Gfx(graphics), mouseX, mouseY, partialTick);
    }

    @Override
    public final void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        draw(new Gfx(graphics), mouseX, mouseY, partialTick);
    }
}
