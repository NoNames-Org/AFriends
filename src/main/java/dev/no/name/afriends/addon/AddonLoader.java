package dev.no.name.afriends.addon;

import dev.no.name.afriends.AFriends;
import dev.no.name.afriends.api.AFriendsAddon;
import dev.no.name.afriends.api.AFriendsApi;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Подключает аддоны: свои встроенные части (сеть) первыми, потом entrypoint «afriends» других модов. */
public final class AddonLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(AFriends.NAME);
    private static final String ENTRYPOINT = AFriends.MOD_ID;

    private AddonLoader() {
    }

    public static void load(AFriendsApi api, List<AFriendsAddon> builtIn) {
        builtIn.forEach(addon -> addon.register(api));
        for (EntrypointContainer<AFriendsAddon> container
                : FabricLoader.getInstance().getEntrypointContainers(ENTRYPOINT, AFriendsAddon.class)) {
            String mod = container.getProvider().getMetadata().getId();
            try {
                container.getEntrypoint().register(api);
                LOGGER.info("{}: подключён аддон {}", AFriends.NAME, mod);
            } catch (RuntimeException | LinkageError e) {
                LOGGER.error("{}: аддон {} не подключился", AFriends.NAME, mod, e);
            }
        }
    }
}
