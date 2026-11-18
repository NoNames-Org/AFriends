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
        Minecraft.getInstance().gui.setScreen(screen);
    }

    public static void textInputFocus(Object owner, boolean focused) {
        Minecraft.getInstance().textInputManager().onTextInputFocusChange(owner, focused);
    }

    public static void stopTextInput() {
        Minecraft.getInstance().textInputManager().stopTextInput();
    }

    public static boolean isKeyDown(int key) {
        return InputConstants.isKeyDown(key);
    }

    public static void addWidget(Screen screen, AbstractWidget widget) {
        Screens.getWidgets(screen).add(widget);
    }

    public static List<AbstractWidget> widgets(Screen screen) {
        return List.copyOf(Screens.getWidgets(screen));
    }

    public static Screen currentScreen() {
        return Minecraft.getInstance().gui.screen();
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
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(message);
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

    // где игрок сейчас: адрес сервера, одиночная игра или меню — открытым текстом для портала админа
    public static String currentServer() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return "В меню";
        }
        if (minecraft.hasSingleplayerServer()) {
            return "Одиночная игра";
        }
        net.minecraft.client.multiplayer.ServerData server = minecraft.getCurrentServer();
        return server != null ? server.ip : "Сервер";
    }

    // Окна системы для файлов через SDL3 (в 26.3 игра на SDL, TinyFileDialogs нет). SDL зовёт ответ сам, когда игрок
    // выбрал файл; фильтры и обработчик должны жить до этого момента — держим их и освобождаем в ответе
    public static void chooseFile(String title, String[] patterns, String description, java.util.function.Consumer<java.nio.file.Path> chosen) {
        showDialog(false, patterns, description, null, chosen);
    }

    public static void saveFile(String title, String suggested, String[] patterns, String description, java.util.function.Consumer<java.nio.file.Path> chosen) {
        showDialog(true, patterns, description, suggested, chosen);
    }

    private static void showDialog(boolean save, String[] patterns, String description, String location,
                                   java.util.function.Consumer<java.nio.file.Path> chosen) {
        // SDL ждёт шаблоны без «*.» и через «;»: png;jpg;gif
        StringBuilder joined = new StringBuilder();
        for (String pattern : patterns) {
            if (!joined.isEmpty()) {
                joined.append(';');
            }
            joined.append(pattern.startsWith("*.") ? pattern.substring(2) : pattern);
        }
        java.nio.ByteBuffer name = org.lwjgl.system.MemoryUtil.memUTF8(description);
        java.nio.ByteBuffer pattern = org.lwjgl.system.MemoryUtil.memUTF8(joined.toString());
        org.lwjgl.sdl.SDL_DialogFileFilter.Buffer filters = org.lwjgl.sdl.SDL_DialogFileFilter.calloc(1);
        filters.get(0).name(name).pattern(pattern);
        org.lwjgl.sdl.SDL_DialogFileCallback[] holder = new org.lwjgl.sdl.SDL_DialogFileCallback[1];
        holder[0] = org.lwjgl.sdl.SDL_DialogFileCallback.create((userdata, files, filter) -> {
            String result = null;
            if (files != 0) {
                long first = org.lwjgl.system.MemoryUtil.memGetAddress(files);
                if (first != 0) {
                    result = org.lwjgl.system.MemoryUtil.memUTF8(first);
                }
            }
            String path = result;
            Minecraft.getInstance().execute(() -> {
                chosen.accept(path == null ? null : java.nio.file.Path.of(path));
                filters.free();
                org.lwjgl.system.MemoryUtil.memFree(name);
                org.lwjgl.system.MemoryUtil.memFree(pattern);
                holder[0].free();
            });
        });
        long window = Minecraft.getInstance().getWindow().handle();
        Minecraft.getInstance().execute(() -> {
            if (save) {
                org.lwjgl.sdl.SDLDialog.SDL_ShowSaveFileDialog(holder[0], 0L, window, filters, location);
            } else {
                org.lwjgl.sdl.SDLDialog.SDL_ShowOpenFileDialog(holder[0], 0L, window, filters, (CharSequence) null, false);
            }
        });
    }
}
