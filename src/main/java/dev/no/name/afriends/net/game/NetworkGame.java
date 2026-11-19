package dev.no.name.afriends.net.game;

import dev.no.name.afriends.net.announcements.AnnouncementFeed;
import dev.no.name.afriends.addon.SendPolicy;
import dev.no.name.afriends.AFriends;
import dev.no.name.afriends.core.AccountScope;
import dev.no.name.afriends.core.FriendManager;
import dev.no.name.afriends.core.ServerScope;
import dev.no.name.afriends.core.SocialData;
import dev.no.name.afriends.compat.Compat;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.net.game.friends.FriendSync;
import dev.no.name.afriends.net.game.friends.NetworkAddon;
import dev.no.name.afriends.net.service.LinkService;
import dev.no.name.afriends.net.service.LocalProfile;
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
    private final BadgeText badges;
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
    // у каждого аккаунта Minecraft свои ключи сети; account — под каким сейчас подключены
    private final AccountFolders accounts;
    private String account;

    // save — записать конфиг, когда друг сам удалил нас и список изменился
    public NetworkGame(SocialData social, LinkSettings settings, Runnable save, AnnouncementFeed announcements,
                       SendPolicy sendPolicy) {
        this.social = social;
        this.settings = settings;
        Path root = SharedLinkFolder.root();
        Path folder = SharedLinkFolder.linkFolder(root);
        SharedLinkFolder.migrateQuietly(
                FabricLoader.getInstance().getConfigDir().resolve(AFriends.MOD_ID).resolve("link"), folder);
        this.lockFile = SharedLinkFolder.lockFile(root);
        this.accounts = new AccountFolders(folder);
        this.account = AccountScope.current();
        Path own = accounts.folder(account, AccountScope.profileId());
        this.incoming = new IncomingChat(social, own.resolve("parked.json"));
        // картинки переписки — рядом с папкой сети, общие для всех аккаунтов
        this.link = new LinkService(own, folder.resolveSibling("media"), GameAccount::current, incoming, announcements);
        link.switchAccount(own, accounts.others(account));
        this.friends = new FriendSync(link, name -> {
            if (social.accountWide().removeFriend(name)) {
                save.run();
            }
        });
        this.direct = new DirectMessages(social, link, sendPolicy);
        incoming.setOnDelivered(direct::delivered);
        incoming.setLeftGroup(direct::hasLeftGroup);
        this.badges = new BadgeText(link.badges(), () -> running);
    }

    public NetworkAddon addon() {
        return new NetworkAddon(friends, link, settings);
    }

    public BadgeText badges() {
        return badges;
    }

    public LinkService link() {
        return link;
    }

    public DirectMessages direct() {
        return direct;
    }

    public void tick(Minecraft client) {
        followAccount();
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
                Theme.ACCENT & 0xFFFFFF, settings.visible(), Compat.currentServer());
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

    // игрок сменил аккаунт прямо в игре (In-Game Account Switcher и т. п.): сеть переподключается ключами
    // нового аккаунта, друзья и переписки берутся его (см. SocialData), прежний аккаунт становится твинком
    private void followAccount() {
        String now = AccountScope.current();
        if (now.equals(account)) {
            return;
        }
        account = now;
        Path own = accounts.folder(now, AccountScope.profileId());
        incoming.useParked(own.resolve("parked.json"));
        friends.reset();
        lastProfile = null;
        wasOnline = false;
        tab.forget();
        link.switchAccount(own, accounts.others(now));
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
