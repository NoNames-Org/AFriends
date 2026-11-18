package dev.no.name.afriends.gui.sound;

import dev.no.name.afriends.core.NotifySettings;
import dev.no.name.afriends.core.PrivateMessages;
import dev.no.name.afriends.core.ServerScope;
import net.minecraft.client.Minecraft;

import java.util.function.Consumer;

/**
 * Звук и всплывающее уведомление о входящем сообщении. Не чаще раза в полторы секунды: при входе
 * в мир отложенные сообщения раскладываются пачкой, и звенеть на каждое — шум. Заметки и свои
 * сообщения сюда не попадают. {@code onAlert} получает ключ чата, когда все проверки пройдены:
 * клиент по нему достаёт заголовок и показывает тост в правом верхнем углу
 */
public final class MessageNotifier {
    private static final long MIN_GAP_MS = 1500;

    private final NotifySettings settings;
    private final Consumer<String> onAlert;
    private long lastRing;

    public MessageNotifier(NotifySettings settings, Consumer<String> onAlert) {
        this.settings = settings;
        this.onAlert = onAlert;
    }

    public void incoming(String chat) {
        boolean ring = settings.shouldRing(chat);
        boolean toast = settings.shouldToast(chat);
        if (PrivateMessages.NOTES.equals(chat) || !ring && !toast || Minecraft.getInstance().player == null
                && ServerScope.NONE.equals(ServerScope.current())) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastRing < MIN_GAP_MS) {
            return;
        }
        lastRing = now;
        if (ring) {
            UiSounds.play(UiSound.MESSAGE);
        }
        // «Уведомления» в настройках мессенджера выключают карточку, звук живёт своим выключателем
        if (toast) {
            onAlert.accept(chat);
        }
    }
}
