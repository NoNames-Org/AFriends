package dev.no.name.blooddesigner.gui.sound;

import dev.no.name.blooddesigner.core.NotifySettings;
import dev.no.name.blooddesigner.core.PrivateMessages;
import dev.no.name.blooddesigner.core.ServerScope;
import net.minecraft.client.Minecraft;

/**
 * Звук входящего сообщения. Не чаще раза в полторы секунды: при входе в мир отложенные сообщения
 * раскладываются пачкой, и звенеть на каждое — шум. Заметки и свои сообщения сюда не попадают
 */
public final class MessageNotifier {
    private static final long MIN_GAP_MS = 1500;

    private final NotifySettings settings;
    private long lastRing;

    public MessageNotifier(NotifySettings settings) {
        this.settings = settings;
    }

    public void incoming(String chat) {
        if (PrivateMessages.NOTES.equals(chat) || !settings.shouldRing(chat) || Minecraft.getInstance().player == null
                && ServerScope.NONE.equals(ServerScope.current())) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastRing < MIN_GAP_MS) {
            return;
        }
        lastRing = now;
        UiSounds.play(UiSound.MESSAGE);
    }
}
