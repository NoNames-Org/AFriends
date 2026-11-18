package dev.no.name.afriends.net.game;

import dev.no.name.afriends.net.model.Presence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

// «Зайти к другу»: выйти из текущего мира и подключиться к его серверу
public final class LinkJoin {
    private LinkJoin() {
    }

    public static boolean canJoin(Presence presence) {
        if (presence == null || !presence.joinable()) {
            return false;
        }
        ServerData current = Minecraft.getInstance().getCurrentServer();
        // уже там
        return current == null || !current.ip.equalsIgnoreCase(presence.server());
    }

    public static void join(Presence presence) {
        if (!canJoin(presence) || !ServerAddress.isValidAddress(presence.server())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        // как кнопка «Отключиться» в паузе: одиночный мир сохраняется, с сервера выходим по-человечески
        if (minecraft.level != null) {
            minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
        }
        Screen parent = new JoinMultiplayerScreen(new TitleScreen());
        ServerData data = new ServerData(presence.server(), presence.server(), ServerData.Type.OTHER);
        ConnectScreen.startConnecting(parent, minecraft, ServerAddress.parseString(presence.server()), data, false, null);
    }
}
