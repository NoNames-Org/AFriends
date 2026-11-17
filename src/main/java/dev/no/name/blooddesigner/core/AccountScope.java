package dev.no.name.blooddesigner.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.util.Locale;
import java.util.UUID;

/**
 * Аккаунт Minecraft, под которым сейчас идёт игра. У каждого аккаунта свои друзья, переписки и ключи сети.
 * Аккаунт может смениться прямо в игре (In-Game Account Switcher и похожие моды): мод замечает это сам,
 * поэтому от конкретного переключателя аккаунтов он не зависит.
 * Аккаунт узнаётся по нику, а не по UUID: у офлайн-аккаунтов UUID зависит от того, кто запустил игру
 * (лаунчер и переключатель аккаунтов дают одному нику разные), а ник в сети и так один на игрока
 */
public final class AccountScope {
    public static final String UNKNOWN = "unknown";

    private AccountScope() {
    }

    // ник в нижнем регистре
    public static String current() {
        User user = Minecraft.getInstance().getUser();
        return user == null || user.getName() == null || user.getName().isBlank() ? UNKNOWN : user.getName().toLowerCase(Locale.ROOT);
    }

    // UUID профиля без дефисов: так папки ключей назывались в тестовых сборках, по нему они переезжают к нику
    public static String profileId() {
        User user = Minecraft.getInstance().getUser();
        UUID id = user == null ? null : user.getProfileId();
        return id == null ? UNKNOWN : id.toString().replace("-", "");
    }
}
