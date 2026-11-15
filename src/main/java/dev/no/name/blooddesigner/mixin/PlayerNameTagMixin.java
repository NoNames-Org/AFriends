package dev.no.name.blooddesigner.mixin;

import dev.no.name.blooddesigner.net.game.BadgeText;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// значок сети перед ником над головой игрока
@Mixin(EntityRenderer.class)
public abstract class PlayerNameTagMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void blooddesigner$badge(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo callback) {
        if (entity instanceof Player player && state.nameTag != null) {
            state.nameTag = BadgeText.prefixed(player.getGameProfile().name(), state.nameTag);
        }
    }
}
