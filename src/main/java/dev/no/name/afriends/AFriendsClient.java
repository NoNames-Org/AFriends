package dev.no.name.afriends;

import dev.no.name.afriends.addon.AddonApi;
import dev.no.name.afriends.addon.AddonLoader;
import dev.no.name.afriends.addon.AddonRegistry;
import dev.no.name.afriends.compat.Compat;
import dev.no.name.afriends.core.ChatFilter;
import dev.no.name.afriends.core.ConfigStore;
import dev.no.name.afriends.core.DataMigration;
import dev.no.name.afriends.core.FriendCommands;
import dev.no.name.afriends.core.HeadCache;
import dev.no.name.afriends.core.MessageEvents;
import dev.no.name.afriends.core.MessageStore;
import dev.no.name.afriends.core.NotifySettings;
import dev.no.name.afriends.core.SocialData;
import dev.no.name.afriends.core.SoundSettings;
import dev.no.name.afriends.gui.UiServices;
import dev.no.name.afriends.gui.friends.FriendsUi;
import dev.no.name.afriends.gui.media.GifFavorites;
import dev.no.name.afriends.gui.media.ImageTextures;
import dev.no.name.afriends.gui.media.VoiceClips;
import dev.no.name.afriends.gui.media.VoicePlayer;
import dev.no.name.afriends.gui.notify.MessageToasts;
import dev.no.name.afriends.gui.sound.MessageNotifier;
import dev.no.name.afriends.gui.sound.UiSounds;
import dev.no.name.afriends.gui.theme.ThemeSettings;
import dev.no.name.afriends.net.announcements.AnnouncementFeed;
import dev.no.name.afriends.net.game.LinkSettings;
import dev.no.name.afriends.net.game.NetworkGame;
import dev.no.name.afriends.net.game.SharedLinkFolder;
import dev.no.name.afriends.net.update.VersionCheck;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.util.List;

/**
 * Корень сборки: здесь создаётся каждый сервис мода и передаётся тем, кому нужен, через конструктор.
 * Синглтонов нет; единственная статика — {@link ClientHooks} для миксинов, которые создаёт сама игра
 */
public class AFriendsClient implements ClientModInitializer {
    private static final long THEME_SAVE_DELAY_MS = 600;

    private final ThemeSettings theme = new ThemeSettings();
    private final LinkSettings linkSettings = new LinkSettings();
    private final NotifySettings notifySettings = new NotifySettings();
    private final SoundSettings soundSettings = new SoundSettings();
    private final MessageToasts toasts = new MessageToasts();
    private SocialData socialData;
    private HeadCache heads;
    private MessageStore messageStore;
    private ConfigStore configStore;
    private NetworkGame network;
    private long themeChangedAt;

    @Override
    public void onInitializeClient() {
        // до первого чтения файлов: данные BloodDesigner переезжают в папки AFriends
        migrateLegacyData();
        heads = new HeadCache(HeadCache.defaultFile());
        socialData = new SocialData(heads, notifySettings::isMuted);
        messageStore = new MessageStore(socialData);
        configStore = new ConfigStore(socialData, theme, linkSettings, notifySettings, soundSettings);

        Compat.init(toasts);
        heads.load();
        messageStore.load();
        soundSettings.setApply(UiSounds::setEnabled);
        configStore.load();
        soundSettings.setOnChange(configStore::save);
        linkSettings.setOnChange(configStore::save);
        notifySettings.setOnChange(configStore::save);
        theme.setOnChange(() -> themeChangedAt = System.currentTimeMillis());
        listenToMessages();

        AddonRegistry addons = new AddonRegistry();
        AnnouncementFeed announcements = new AnnouncementFeed();
        // сеть друзей и ЛС: свой сервер, сквозное шифрование
        network = new NetworkGame(socialData, linkSettings, configStore::save, announcements, addons);
        ClientHooks.install(network.badges());

        // аддоны встраиваются до того, как появятся окна; сеть — встроенная часть, она первая
        AddonLoader.load(new AddonApi(addons, socialData, configStore::save, network.direct(), heads),
                List.of(network.addon()));
        new FriendCommands(socialData, configStore::save, addons).register();
        new ChatFilter(socialData).register();

        UiServices ui = new UiServices(addons, new ImageTextures(), new GifFavorites(),
                new VoiceClips(new VoicePlayer()), announcements, new VersionCheck());
        new FriendsUi(socialData, theme, linkSettings, notifySettings, soundSettings, network, configStore::save, ui)
                .register();

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> close());
    }

    private void listenToMessages() {
        MessageNotifier notifier = new MessageNotifier(notifySettings,
                chat -> toasts.push(socialData.messages().displayName(chat)));
        socialData.addListener(new MessageEvents() {
            @Override
            public void changed() {
                messageStore.markDirty();
            }

            @Override
            public void incoming(String chat) {
                notifier.incoming(chat);
            }
        });
    }

    private void tick(Minecraft client) {
        socialData.tick();
        messageStore.tick();
        heads.tick();
        network.tick(client);
        if (themeChangedAt > 0 && System.currentTimeMillis() - themeChangedAt >= THEME_SAVE_DELAY_MS) {
            themeChangedAt = 0;
            configStore.save();
        }
    }

    private void close() {
        configStore.save();
        messageStore.save();
        network.close();
        heads.save();
    }

    private static void migrateLegacyData() {
        Path config = FabricLoader.getInstance().getConfigDir();
        DataMigration.migrateQuietly(config.resolve(AFriends.LEGACY_MOD_ID), config.resolve(AFriends.MOD_ID));
        Path shared = SharedLinkFolder.root();
        DataMigration.migrateQuietly(SharedLinkFolder.legacyRoot(shared), shared);
    }
}
