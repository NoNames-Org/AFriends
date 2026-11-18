package dev.no.name.afriends.api;

/**
 * Точка входа аддона. В fabric.mod.json аддона:
 * <pre>
 *   "entrypoints": { "afriends": ["полное.имя.КлассаАддона"] },
 *   "depends": { "afriends": ">=2.0.0" }
 * </pre>
 * AFriends вызывает {@link #register} один раз при запуске, до появления окон. Если AFriends не стоит,
 * класс аддона никто не загружает.
 * <p>
 * Если register бросит исключение, AFriends напишет в лог, но уже сделанные регистрации не откатываются:
 * сначала делайте то, что может упасть (чтение конфига и т. п.), и только потом регистрируйте части.
 */
@FunctionalInterface
public interface AFriendsAddon {
    void register(AFriendsApi api);
}
