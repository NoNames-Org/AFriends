package dev.no.name.blooddesigner.net.game;

import dev.no.name.blooddesigner.core.PrivateMessage;
import dev.no.name.blooddesigner.core.ServerScope;
import dev.no.name.blooddesigner.core.SocialData;
import dev.no.name.blooddesigner.net.chat.InboxListener;
import dev.no.name.blooddesigner.net.model.LinkFriend;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;

// Расшифрованные сообщения из сети — в переписку BloodDesigner на потоке игры.
// В меню переписки нет, поэтому сообщения ждут входа в мир
final class IncomingChat implements InboxListener {
    private static final String UNREADABLE = "⚠ Сообщение не расшифровалось. Если повторится — сверьте отпечаток ключей";

    private final SocialData social;
    private final ParkedMessages parked;

    IncomingChat(SocialData social, Path parkedFile) {
        this.social = social;
        this.parked = new ParkedMessages(parkedFile);
    }

    @Override
    public void text(LinkFriend from, String text, long time) {
        Minecraft.getInstance().execute(() -> deliver(from.name(), new PrivateMessage(false, text, time)));
    }

    @Override
    public void unreadable(LinkFriend from, long time) {
        Minecraft.getInstance().execute(() -> deliver(from.name(), new PrivateMessage(false, UNREADABLE, time)));
    }

    // раз в тик: зашли в мир — отложенное раскладывается по перепискам
    void tick() {
        if (!parked.isEmpty() && !ServerScope.NONE.equals(ServerScope.current())) {
            parked.takeAll().forEach(item -> deliver(item.partner(), item.message()));
        }
    }

    private void deliver(String partner, PrivateMessage message) {
        if (ServerScope.NONE.equals(ServerScope.current())) {
            parked.add(partner, message);
            return;
        }
        if (social.ignored().isIgnored(partner)) {
            return;
        }
        social.messages().add(partner, message);
    }
}
