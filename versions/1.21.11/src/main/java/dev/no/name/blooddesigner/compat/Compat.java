package dev.no.name.blooddesigner.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.inventory.ClickType;

import java.util.List;

public final class Compat {
    private Compat() {
    }

    public static void init() {
        MenuPipelines.init();
    }

    public static void setScreen(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    public static void textInputFocus(Object owner, boolean focused) {
    }

    public static void stopTextInput() {
    }

    public static boolean isKeyDown(int key) {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key);
    }

    public static void addWidget(Screen screen, AbstractWidget widget) {
        Screens.getButtons(screen).add(widget);
    }

    public static List<AbstractWidget> widgets(Screen screen) {
        return List.copyOf(Screens.getButtons(screen));
    }

    public static Screen currentScreen() {
        return Minecraft.getInstance().screen;
    }

    /** Shift+ЛКМ по слоту серверного меню. */
    public static void quickMoveSlot(int containerId, int slot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null && minecraft.player != null) {
            minecraft.gameMode.handleInventoryMouseClick(containerId, slot, 0, ClickType.QUICK_MOVE, minecraft.player);
        }
    }

    /** Закрыть серверное меню так же, как Esc в ванильном сундуке: сервер получает пакет закрытия. */
    public static void closeContainer() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.closeContainer();
        }
    }

    public static void clickSlot(int containerId, int slot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null && minecraft.player != null) {
            minecraft.gameMode.handleInventoryMouseClick(containerId, slot, 0, ClickType.PICKUP, minecraft.player);
        }
    }

    // строка в чат только для себя: сервер её не видит
    public static void localMessage(net.minecraft.network.chat.Component message) {
        Minecraft.getInstance().gui.getChat().addMessage(message);
    }
}
