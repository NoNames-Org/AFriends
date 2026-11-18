package dev.no.name.afriends.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import dev.no.name.afriends.gui.render.blur.MenuBlur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private RenderTarget mainRenderTarget;

    @Shadow
    @Final
    private CrossFrameResourcePool resourcePool;

    @Inject(method = "processBlurEffect", at = @At("HEAD"), cancellable = true)
    private void afriends$processMenuBlur(CallbackInfo callback) {
        if (!MenuBlur.hasRequest()) {
            return;
        }
        callback.cancel();
        Identifier chainId = MenuBlur.consumeChain();
        if (chainId == null) {
            return;
        }
        PostChain chain = minecraft.getShaderManager().getPostChain(chainId, LevelTargetBundle.MAIN_TARGETS);
        if (chain != null) {
            chain.process(mainRenderTarget, resourcePool);
        }
    }
}
