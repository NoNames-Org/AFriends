package dev.no.name.blooddesigner.gui.modes;

import dev.no.name.blooddesigner.core.FeatureToggles;
import dev.no.name.blooddesigner.core.FeatureToggles.Feature;
import dev.no.name.blooddesigner.compat.Compat;
import dev.no.name.blooddesigner.core.modes.BloodstoneServer;
import dev.no.name.blooddesigner.core.modes.ModeFavorites;
import dev.no.name.blooddesigner.core.modes.ModeLauncher;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;

public final class PauseMenuHook {
    private static final int BUTTON_WIDTH = 204;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 4;

    private static final int MIN_MENU_BUTTON_WIDTH = 98;

    private final ModeFavorites favorites;
    private final ModeLauncher launcher;

    public PauseMenuHook(ModeFavorites favorites, ModeLauncher launcher) {
        this.favorites = favorites;
        this.launcher = launcher;
    }

    public void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!FeatureToggles.enabled(Feature.MODES)) {
                return;
            }
            if (!(screen instanceof PauseScreen pause) || !pause.showsPauseMenu() || !BloodstoneServer.isConnected()) {
                return;
            }

            int bottom = -1;
            int top = Integer.MAX_VALUE;
            for (AbstractWidget widget : Compat.widgets(screen)) {
                if (widget.getWidth() >= MIN_MENU_BUTTON_WIDTH) {
                    bottom = Math.max(bottom, widget.getY() + widget.getHeight());
                    top = Math.min(top, widget.getY());
                }
            }
            int y = bottom < 0 ? height / 4 : bottom + GAP;
            if (y + BUTTON_HEIGHT > height - GAP && top != Integer.MAX_VALUE) {
                y = Math.max(GAP, top - GAP - BUTTON_HEIGHT);
            }
            Button button = Button.builder(Component.literal("Выбрать режим"),
                            pressed -> Compat.setScreen(new ModeSelectScreen(screen, favorites, launcher)))
                    .bounds(width / 2 - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build();
            Compat.addWidget(screen, button);
        });
    }
}
