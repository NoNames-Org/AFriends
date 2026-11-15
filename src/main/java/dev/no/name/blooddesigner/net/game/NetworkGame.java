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

import java.nio.file.Path;

// Сеть внутри игры: создаёт службу, раз в тик сообщает ей статус игрока, подмешивает друзей по сети
// в список сервера и раскладывает отложенные сообщения. Всё здесь — на потоке игры
public final class NetworkGame {
    private static final int LOOKUP_EVERY_TICKS = 300;

    private final SocialData social;
    private final LinkSettings settings;
    private final LinkService link;
    private final FriendSync friends;
    private final IncomingChat incoming;
    private final DirectMessages direct;
    private final PresenceSource presence = new PresenceSource();
    private LocalProfile lastProfile;
    // первый запрос значков — через пару секунд после входа, дальше раз в 15 секунд
    private int lookupTicks = LOOKUP_EVERY_TICKS - 40;
    private boolean running;

    // save — записать конфиг, когда друг сам удалил нас и список изменился
    public NetworkGame(SocialData social, LinkSettings settings, Runnable save) {
        this.social = social;
        this.settings = settings;
        Path folder = FabricLoader.getInstance().getConfigDir().resolve(BloodDesigner.MOD_ID).resolve("link");
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
        if (settings.enabled() != running) {
            running = settings.enabled();
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
        friends.tick();
        String scope = ServerScope.current();
        if (!ServerScope.NONE.equals(scope)) {
            friends.merge(social.friends(), scope);
            if (++lookupTicks >= LOOKUP_EVERY_TICKS) {
                lookupTicks = 0;
                link.lookup(FriendManager.onlinePlayerNames());
            }
        }
    }

    public void close() {
        link.close();
    }
}
