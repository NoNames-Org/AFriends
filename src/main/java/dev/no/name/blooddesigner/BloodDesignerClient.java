package dev.no.name.blooddesigner;

import dev.no.name.blooddesigner.addon.Addons;
import dev.no.name.blooddesigner.compat.Compat;
import dev.no.name.blooddesigner.core.ChatFilter;
import dev.no.name.blooddesigner.core.ConfigStore;
import dev.no.name.blooddesigner.core.FriendCommands;
import dev.no.name.blooddesigner.core.HeadCache;
import dev.no.name.blooddesigner.core.MessageStore;
import dev.no.name.blooddesigner.core.SocialData;
import dev.no.name.blooddesigner.core.modes.ModeFavorites;
import dev.no.name.blooddesigner.core.modes.ModeLauncher;
import dev.no.name.blooddesigner.core.party.PartyService;
import dev.no.name.blooddesigner.gui.friends.FriendsUi;
import dev.no.name.blooddesigner.gui.modes.PauseMenuHook;
import dev.no.name.blooddesigner.gui.theme.ThemeSettings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class BloodDesignerClient implements ClientModInitializer {
    private final SocialData socialData = new SocialData();
    private final MessageStore messageStore = new MessageStore(socialData);
    private final ModeFavorites modeFavorites = new ModeFavorites();
    private final ThemeSettings theme = new ThemeSettings();
    private final ConfigStore configStore = new ConfigStore(socialData, modeFavorites, theme);

    private static final long THEME_SAVE_DELAY_MS = 600;
    private long themeChangedAt;

    @Override
    public void onInitializeClient() {
        Compat.init();
        HeadCache.init();
        messageStore.load();
        socialData.setOnMessagesChange(messageStore::markDirty);
        configStore.load();
        modeFavorites.setOnChange(configStore::save);
        theme.setOnChange(() -> themeChangedAt = System.currentTimeMillis());

        PartyService party = new PartyService();
        party.register();
        // дополнения других модов встраиваются до того, как появятся окна
        Addons.load(socialData, party, configStore::save);
        new FriendCommands(socialData, configStore::save).register();
        new ChatFilter(socialData, party).register();

        new FriendsUi(socialData, party, theme, configStore::save, () -> {
            socialData.trimMessages();
            themeChangedAt = System.currentTimeMillis();
        }).register();
        ModeLauncher launcher = new ModeLauncher();
        launcher.register();
        new PauseMenuHook(modeFavorites, launcher).register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            socialData.tick();
            party.tick();
            messageStore.tick();
            HeadCache.get().tick();
            if (themeChangedAt > 0 && System.currentTimeMillis() - themeChangedAt >= THEME_SAVE_DELAY_MS) {
                themeChangedAt = 0;
                configStore.save();
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            configStore.save();
            messageStore.save();
            HeadCache.get().save();
        });
    }
}
