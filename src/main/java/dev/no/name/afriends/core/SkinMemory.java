package dev.no.name.afriends.core;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;

/** Где помнятся скины игроков, которых сейчас нет на сервере. */
public interface SkinMemory {
    SkinMemory NONE = new SkinMemory() {
        @Override
        public PlayerSkin skin(String name) {
            return null;
        }

        @Override
        public void remember(PlayerInfo info) {
        }
    };

    /** Скин по нику или null, если не знаем. */
    PlayerSkin skin(String name);

    void remember(PlayerInfo info);
}
