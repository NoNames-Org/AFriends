package dev.no.name.blooddesigner.net.game;

import dev.no.name.blooddesigner.BloodDesigner;
import dev.no.name.blooddesigner.core.FriendManager;
import dev.no.name.blooddesigner.core.ServerScope;
import dev.no.name.blooddesigner.core.SocialData;
import dev.no.name.blooddesigner.gui.Theme;
import dev.no.name.blooddesigner.net.game.friends.FriendSync;
import dev.no.name.blooddesigner.net.game.friends.NetworkAddon;
import dev.no.name.blooddesigner.net.service.LinkService;
import dev.no.name.blooddesigner.net.service.LocalProfile;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.util.List;

// Сеть внутри игры: создаёт службу, раз в тик сообщает ей статус игрока, подмешивает друзей по сети
// в список сервера и раскладывает отложенные сообщения. Всё здесь — на потоке игры
public final class NetworkGame {

    private final SocialData social;
    private final LinkSettings settings;
    private final LinkService link;
    private final FriendSync friends;
    private final IncomingChat incoming;
    private final DirectMessages direct;
    private final PresenceSource presence = new PresenceSource();
    private LocalProfile lastProfile;
    private final TabWatcher tab = new TabWatcher();
    private boolean wasOnline;
    private boolean running;
    // общая папка ключей сети одна на компьютер; сетью владеет та копия игры, что держит блокировку
    private static final long LOCK_RETRY_MS = 10_000;
    private final Path lockFile;
    private FileLock lock;
    private long nextLockTry;

    // save — записать конфиг, когда друг сам удалил нас и список изменился
    public NetworkGame(SocialData social, LinkSettings settings, Runnable save) {
        this.social = social;
        this.settings = settings;
        Path root = SharedLinkFolder.root();
        Path folder = SharedLinkFolder.linkFolder(root);
        SharedLinkFolder.migrateQuietly(
                FabricLoader.getInstance().getConfigDir().resolve(BloodDesigner.MOD_ID).resolve("link"), folder);
        this.lockFile = SharedLinkFolder.lockFile(root);
        this.incoming = new IncomingChat(social, folder.resolve("parked.json"));
        this.link = new LinkService(folder, GameAccount::current, incoming);
        this.friends = new FriendSync(link, name -> {
            if (social.removeFriendEverywhere(name)) {
                save.run();
            }
        });
        this.direct = new DirectMessages(social, link);
        BadgeText.install(link.badges(), () -> running);
    }

    public NetworkAddon addon() {
        return new NetworkAddon(friends, link, settings);
    }

    public LinkService link() {
        return link;
    }

    public DirectMessages direct() {
        return direct;
    }

    public void tick(Minecraft client) {
        boolean wanted = settings.enabled();
        if (wanted && lock == null && System.currentTimeMillis() >= nextLockTry) {
            lock = SharedLinkFolder.tryLock(lockFile);
            nextLockTry = System.currentTimeMillis() + LOCK_RETRY_MS;
        }
        SharedLinkFolder.setBusy(wanted && lock == null);
        boolean shouldRun = wanted && lock != null;
        if (shouldRun != running) {
            running = shouldRun;
            if (running) {
                lastProfile = null;
                link.start();
            } else {
                link.stop();
            }
        }
        if (!running) {
            return;
        }
        LocalProfile profile = new LocalProfile(presence.current(client, settings.shareServer(), settings.status()),
                Theme.ACCENT & 0xFFFFFF, settings.visible());
        if (lastProfile == null || !profile.equals(lastProfile)) {
            lastProfile = profile;
            link.updateProfile(profile);
        }
        incoming.tick();
        direct.tick();
        friends.tick();
        String scope = ServerScope.current();
        if (!ServerScope.NONE.equals(scope)) {
            friends.merge(social.friends(), scope);
            lookupArrivals();
        }
    }

    // значки: только когда в табе кто-то новый или связь только что появилась, без опроса по таймеру
    private void lookupArrivals() {
        boolean online = friends.active();
        if (online && !wasOnline) {
            tab.forget();
        }
        wasOnline = online;
        if (!online) {
            return;
        }
        List<String> arrived = tab.arrived(FriendManager.onlinePlayerNames());
        if (!arrived.isEmpty()) {
            link.lookup(arrived);
        }
    }

    public void close() {
        link.close();
    }
}
