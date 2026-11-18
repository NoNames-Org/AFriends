package dev.no.name.afriends.compat;

import dev.no.name.afriends.AFriends;
import dev.no.name.afriends.gui.notify.MessageToasts;
import dev.no.name.afriends.gui.notify.ToastDraw;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** Слой HUD для тостов на 26.x: элемент HUD получает GuiGraphicsExtractor в фазе извлечения. */
public final class Hud {
    private Hud() {
    }

    public static void init() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(AFriends.MOD_ID, "toasts"),
                (extractor, delta) -> MessageToasts.get().render(new Draw(extractor), System.currentTimeMillis()));
    }

    private record Draw(GuiGraphicsExtractor graphics) implements ToastDraw {
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
            graphics.text(Minecraft.getInstance().font, text, x, y, argb, false);
        }
    }
}
