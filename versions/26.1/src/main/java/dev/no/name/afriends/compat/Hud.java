package dev.no.name.afriends.compat;

import com.mojang.blaze3d.platform.Window;
import dev.no.name.afriends.AFriends;
import dev.no.name.afriends.gui.notify.MessageToasts;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** Слой HUD для тостов на 26.x: элемент HUD получает GuiGraphicsExtractor, тосты рисуются нашим рендером. */
public final class Hud {
    private Hud() {
    }

    public static void init(MessageToasts toasts) {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(AFriends.MOD_ID, "toasts"), (extractor, delta) -> {
            Window window = Minecraft.getInstance().getWindow();
            toasts.render(new Gfx(extractor), window.getGuiScaledWidth(), window.getGuiScale(),
                    System.currentTimeMillis());
        });
    }
}
