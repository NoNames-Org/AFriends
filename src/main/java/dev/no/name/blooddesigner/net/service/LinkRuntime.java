package dev.no.name.blooddesigner.net.service;

import dev.no.name.blooddesigner.net.chat.Inbox;
import dev.no.name.blooddesigner.net.chat.InboxListener;
import dev.no.name.blooddesigner.net.chat.KeyCourier;
import dev.no.name.blooddesigner.net.chat.Outbox;
import dev.no.name.blooddesigner.net.e2e.E2eSessions;
import dev.no.name.blooddesigner.net.e2e.FileSessionStore;
import dev.no.name.blooddesigner.net.e2e.PinnedKeys;
import dev.no.name.blooddesigner.net.identity.Identity;
import dev.no.name.blooddesigner.net.identity.IdentityStore;
import dev.no.name.blooddesigner.net.model.LinkSnapshot;
import dev.no.name.blooddesigner.net.presence.PresenceBook;
import dev.no.name.blooddesigner.net.presence.PresenceKeys;

import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

// Всё, что живёт на потоке сети после загрузки ключей: собирается один раз и дальше только используется
final class LinkRuntime {
    final Identity identity;
    final E2eSessions sessions;
    final PinnedKeys pinned;
    final PresenceBook presence;
    final LinkConnection connection;
    final Outbox outbox;
    final Inbox inbox;
    final KeyCourier courier;
    final LinkSync sync;
    final PrekeyKeeper prekeys;
    final AccountFlow account;

    private LinkRuntime(Identity identity, E2eSessions sessions, PinnedKeys pinned, PresenceBook presence,
                        LinkConnection connection, Outbox outbox, Inbox inbox, KeyCourier courier, LinkSync sync,
                        PrekeyKeeper prekeys, AccountFlow account) {
        this.identity = identity;
        this.sessions = sessions;
        this.pinned = pinned;
        this.presence = presence;
        this.connection = connection;
        this.outbox = outbox;
        this.inbox = inbox;
        this.courier = courier;
        this.sync = sync;
        this.prekeys = prekeys;
        this.account = account;
    }

    // обратные вызовы службы для частей рантайма
    interface Hooks extends LinkConnection.Handler, LinkSync.Result {
    }

    static LinkRuntime load(Path folder, LinkEndpoint endpoint, PlayerAccount player, InboxListener listener,
                            Supplier<LinkSnapshot> snapshot, ScheduledExecutorService worker, Executor io, Hooks hooks,
                            Runnable resync) throws IOException, GeneralSecurityException {
        Identity identity = new IdentityStore(folder.resolve("identity.json")).loadOrCreate();
        FileSessionStore store = new FileSessionStore(folder.resolve("sessions"));
        E2eSessions sessions = new E2eSessions(identity, store);
        PinnedKeys pinned = new PinnedKeys(store);
        PresenceKeys presenceKeys = new PresenceKeys(store);
        PresenceBook presence = new PresenceBook(identity.id(), presenceKeys);
        LinkConnection connection = new LinkConnection(endpoint, identity, worker, io, hooks);
        Outbox outbox = new Outbox(connection, sessions, pinned);
        Inbox inbox = new Inbox(connection, sessions, pinned, presenceKeys, snapshot, listener, resync);
        KeyCourier courier = new KeyCourier(outbox, presenceKeys, worker);
        LinkSync sync = new LinkSync(connection, presence, worker, snapshot, hooks);
        PrekeyKeeper prekeys = new PrekeyKeeper(connection, sessions, identity);
        AccountFlow account = new AccountFlow(connection, identity, player, io);
        return new LinkRuntime(identity, sessions, pinned, presence, connection, outbox, inbox, courier, sync, prekeys, account);
    }
}
