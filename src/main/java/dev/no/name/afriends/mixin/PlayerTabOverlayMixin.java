package dev.no.name.afriends.mixin;

import dev.no.name.afriends.net.game.BadgeText;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// значок сети перед ником в табе; ширина колонки считается по этому же имени, так что не наезжает
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void afriends$badge(PlayerInfo info, CallbackInfoReturnable<Component> callback) {
        callback.setReturnValue(BadgeText.prefixed(info.getProfile().name(), callback.getReturnValue()));
    }
}
