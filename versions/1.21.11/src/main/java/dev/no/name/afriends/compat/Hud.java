package dev.no.name.afriends.compat;

import dev.no.name.afriends.gui.notify.MessageToasts;
import dev.no.name.afriends.gui.notify.ToastDraw;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Слой HUD для тостов на 1.21.11: ванильный HudRenderCallback даёт GuiGraphics напрямую. */
public final class Hud {
    private Hud() {
    }

    public static void init() {
        HudRenderCallback.EVENT.register((graphics, tick) ->
                MessageToasts.get().render(new Draw(graphics), System.currentTimeMillis()));
    }

    private record Draw(GuiGraphics graphics) implements ToastDraw {
        @Override
        public int screenWidth() {
            return Minecraft.getInstance().getWindow().getGuiScaledWidth();
        }

        @Override
        public int textWidth(String text) {
            return Minecraft.getInstance().font.width(text);
        }

        @Override
        public void box(int x, int y, int width, int height, int argb) {
            graphics.fill(x, y, x + width, y + height, argb);
        }

        @Override
        public void line(String text, int x, int y, int argb) {
            graphics.drawString(Minecraft.getInstance().font, text, x, y, argb, false);
        }
    }
}
