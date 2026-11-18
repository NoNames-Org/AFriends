package dev.no.name.afriends.compat;

import com.mojang.blaze3d.platform.Window;
import dev.no.name.afriends.gui.notify.MessageToasts;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;

/** Слой HUD для тостов на 1.21.11: ванильный HudRenderCallback даёт GuiGraphics, тосты рисуются нашим рендером. */
public final class Hud {
    private Hud() {
    }

    public static void init() {
        HudRenderCallback.EVENT.register((graphics, tick) -> {
            Window window = Minecraft.getInstance().getWindow();
            MessageToasts.get().render(new Gfx(graphics), window.getGuiScaledWidth(), window.getGuiScale(),
                    System.currentTimeMillis());
        });
    }
}
