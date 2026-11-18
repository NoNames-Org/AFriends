package dev.no.name.afriends.api;

import net.minecraft.world.entity.player.PlayerSkin;

import java.util.List;

/** Друзья на текущем сервере и друзья по сети. Вне сервера список пустой и не меняется. */
public interface Friends {
    List<String> names();

    boolean contains(String name);

    /** true — добавили; слушатели {@link FriendListener} не вызываются. */
    boolean add(String name);

    /** Игрок сейчас в игре: на этом сервере рядом или в сети AFriends. */
    boolean online(String name);

    /** Игрок на этом же сервере Minecraft (в табе). */
    boolean nearby(String name);

    /** Скин игрока для головы: из таба, из кэша голов или стандартный. */
    PlayerSkin skin(String name);

    /** Ники игроков на этом сервере (из таба). */
    List<String> nearbyNames();
}
