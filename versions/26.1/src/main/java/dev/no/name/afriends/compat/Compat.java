package dev.no.name.afriends.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.inventory.ContainerInput;

import java.util.List;

public final class Compat {
    private Compat() {
    }

    public static void init() {
        MenuPipelines.init();
        Hud.init();
    }

    public static void setScreen(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    public static void textInputFocus(Object owner, boolean focused) {
        Minecraft.getInstance().textInputManager().onTextInputFocusChange(focused);
    }

    public static void stopTextInput() {
        Minecraft.getInstance().textInputManager().stopTextInput();
    }

    public static boolean isKeyDown(int key) {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key);
    }

    public static void addWidget(Screen screen, AbstractWidget widget) {
        Screens.getWidgets(screen).add(widget);
    }

    public static List<AbstractWidget> widgets(Screen screen) {
        return List.copyOf(Screens.getWidgets(screen));
    }

    public static Screen currentScreen() {
        return Minecraft.getInstance().screen;
    }

    /** Shift+ЛКМ по слоту серверного меню. */
    public static void quickMoveSlot(int containerId, int slot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null && minecraft.player != null) {
            minecraft.gameMode.handleContainerInput(containerId, slot, 0, ContainerInput.QUICK_MOVE, minecraft.player);
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
            minecraft.gameMode.handleContainerInput(containerId, slot, 0, ContainerInput.PICKUP, minecraft.player);
        }
    }

    // строка в чат только для себя: сервер её не видит
    public static void localMessage(net.minecraft.network.chat.Component message) {
        Minecraft.getInstance().gui.getChat().addClientSystemMessage(message);
    }

    // общая громкость Minecraft 0..1 — настройки звука AFriends дают её изменить, не выходя из игры
    public static float masterVolume() {
        return Minecraft.getInstance().options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER);
    }

    public static void setMasterVolume(double value) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(value);
        minecraft.options.save();
    }

    // окно системы «выбрать файл»: TinyFileDialogs блокирует поток, поэтому на своём; ответ — на потоке игры, null — отмена
    public static void chooseFile(String title, String[] patterns, String description, java.util.function.Consumer<java.nio.file.Path> chosen) {
        dialogThread(() -> {
            String file;
            try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
                file = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(title, null, filters(stack, patterns), description, false);
            }
            String result = file;
            Minecraft.getInstance().execute(() -> chosen.accept(result == null ? null : java.nio.file.Path.of(result)));
        });
    }

    // окно системы «сохранить как»; null — отмена
    public static void saveFile(String title, String suggested, String[] patterns, String description, java.util.function.Consumer<java.nio.file.Path> chosen) {
        dialogThread(() -> {
            String file;
            try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
                file = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_saveFileDialog(title, suggested, filters(stack, patterns), description);
            }
            String result = file;
            Minecraft.getInstance().execute(() -> chosen.accept(result == null ? null : java.nio.file.Path.of(result)));
        });
    }

    private static org.lwjgl.PointerBuffer filters(org.lwjgl.system.MemoryStack stack, String[] patterns) {
        org.lwjgl.PointerBuffer buffer = stack.mallocPointer(patterns.length);
        for (String pattern : patterns) {
            buffer.put(stack.UTF8(pattern));
        }
        return buffer.flip();
    }

    private static void dialogThread(Runnable work) {
        Thread thread = new Thread(work, "AFriends file dialog");
        thread.setDaemon(true);
        thread.start();
    }
}
