package dev.no.name.afriends;

import dev.no.name.afriends.addon.Addons;
import dev.no.name.afriends.compat.Compat;
import dev.no.name.afriends.core.ChatFilter;
import dev.no.name.afriends.core.ConfigStore;
import dev.no.name.afriends.core.DataMigration;
import dev.no.name.afriends.core.FriendCommands;
import dev.no.name.afriends.core.HeadCache;
import dev.no.name.afriends.core.IncomingAlerts;
import dev.no.name.afriends.core.MessageStore;
import dev.no.name.afriends.core.NotifySettings;
import dev.no.name.afriends.core.SocialData;
import dev.no.name.afriends.core.SoundSettings;
import dev.no.name.afriends.gui.friends.FriendsUi;
import dev.no.name.afriends.gui.sound.MessageNotifier;
import dev.no.name.afriends.gui.sound.UiSounds;
import dev.no.name.afriends.gui.theme.ThemeSettings;
import dev.no.name.afriends.net.game.LinkSettings;
import dev.no.name.afriends.net.game.NetworkGame;
import dev.no.name.afriends.net.game.SharedLinkFolder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.List;

public class AFriendsClient implements ClientModInitializer {
    private static final long THEME_SAVE_DELAY_MS = 600;

    private final SocialData socialData = new SocialData();
    private final ThemeSettings theme = new ThemeSettings();
    private final LinkSettings linkSettings = new LinkSettings();
    private final NotifySettings notifySettings = new NotifySettings();
    private final SoundSettings soundSettings = new SoundSettings();
    private MessageStore messageStore;
    private ConfigStore configStore;
    private long themeChangedAt;

    @Override
    public void onInitializeClient() {
        // до первого чтения файлов: данные BloodDesigner переезжают в папки AFriends
        migrateLegacyData();
        messageStore = new MessageStore(socialData);
        configStore = new ConfigStore(socialData, theme, linkSettings, notifySettings, soundSettings);

        Compat.init();
        HeadCache.init();
        messageStore.load();
        socialData.setOnMessagesChange(messageStore::markDirty);
        socialData.setStaysArchived(notifySettings::isMuted);
        soundSettings.setApply(UiSounds::setEnabled);
        configStore.load();
        soundSettings.setOnChange(configStore::save);
        linkSettings.setOnChange(configStore::save);
        notifySettings.setOnChange(configStore::save);
        MessageNotifier notifier = new MessageNotifier(notifySettings);
        IncomingAlerts.setListener(notifier::incoming);
        // сеть друзей и ЛС: свой сервер, сквозное шифрование
        NetworkGame network = new NetworkGame(socialData, linkSettings, configStore::save);
        theme.setOnChange(() -> themeChangedAt = System.currentTimeMillis());

        // аддоны встраиваются до того, как появятся окна; сеть — встроенная часть, она первая
        Addons.load(socialData, configStore::save, network.direct(), List.of(network.addon()));
        new FriendCommands(socialData, configStore::save).register();
        new ChatFilter(socialData).register();

        new FriendsUi(socialData, theme, linkSettings, notifySettings, soundSettings, network, configStore::save, () -> {
            socialData.trimMessages();
            themeChangedAt = System.currentTimeMillis();
        }).register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            socialData.tick();
            messageStore.tick();
            HeadCache.get().tick();
            network.tick(client);
            if (themeChangedAt > 0 && System.currentTimeMillis() - themeChangedAt >= THEME_SAVE_DELAY_MS) {
                themeChangedAt = 0;
                configStore.save();
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            configStore.save();
            messageStore.save();
            network.close();
            HeadCache.get().save();
        });
    }

    private static void migrateLegacyData() {
        Path config = FabricLoader.getInstance().getConfigDir();
        DataMigration.migrateQuietly(config.resolve(AFriends.LEGACY_MOD_ID), config.resolve(AFriends.MOD_ID));
        Path shared = SharedLinkFolder.root();
        DataMigration.migrateQuietly(SharedLinkFolder.legacyRoot(shared), shared);
    }
}
