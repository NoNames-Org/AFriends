package dev.no.name.afriends;

import dev.no.name.afriends.net.game.BadgeText;
import net.minecraft.network.chat.Component;

/**
 * Единственная статическая точка входа в AFriends — для кода, который вызывает сама игра: миксинов.
 * Их создаёт Minecraft, передать зависимости в конструктор нельзя, поэтому корень сборки один раз
 * кладёт сюда готовые объекты. Остальной код получает зависимости явно и сюда не ходит
 */
public final class ClientHooks {
    private static volatile BadgeText badges;

    private ClientHooks() {
    }

    static void install(BadgeText badgeText) {
        badges = badgeText;
    }

    /** Текст с ником игрока: со значком сети впереди; до запуска мода — как есть. */
    public static Component withBadge(String name, Component text) {
        BadgeText current = badges;
        return current == null ? text : current.prefixed(name, text);
    }
}
