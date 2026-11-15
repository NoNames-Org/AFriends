package dev.no.name.blooddesigner.mixin;

import dev.no.name.blooddesigner.compat.Compat;
import dev.no.name.blooddesigner.core.battlepass.BattlePassHook;
import dev.no.name.blooddesigner.core.modes.ModeLauncher;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class ScreenOpenMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void blooddesigner$hideServerMenu(Screen screen, CallbackInfo callback) {
        if (ModeLauncher.hideScreen(screen)) {
            callback.cancel();
            return;
        }
        Screen replacement = BattlePassHook.replace(screen);
        if (replacement != screen) {
            callback.cancel();
            if (replacement != null) {
                Compat.setScreen(replacement);
            }
        }
    }
}
