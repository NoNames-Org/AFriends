package dev.no.name.afriends.net.game;

import net.minecraft.client.Minecraft;

/** Ответы сети приходят на её потоках; всё, что трогает переписку и окна, переносится на поток игры. */
final class GameThread {
    private GameThread() {
    }

    static void run(Runnable task) {
        Minecraft.getInstance().execute(task);
    }
}
