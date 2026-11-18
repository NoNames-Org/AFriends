package dev.no.name.afriends.net.game;

import dev.no.name.afriends.net.service.LinkService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Отметки «доставлено» и «прочитано» автору сообщения. Копятся и уходят пачкой раз в 3 секунды — по одному
 * сквозному конверту на собеседника. Уже отправленное «прочитано» не повторяется до перезапуска игры.
 * Всё на потоке игры
 */
final class ReceiptSender {
    private static final int MAX_BATCH = 100;
    private static final int MAX_REMEMBERED = 5000;
    // копим отметки 3 секунды: на пачку сообщений — один конверт, сообщениям игрока остаётся запас по лимиту сервера
    private static final int FLUSH_TICKS = 60;

    private final LinkService link;
    private final Map<String, List<String>> delivered = new LinkedHashMap<>();
    private final Map<String, List<String>> read = new LinkedHashMap<>();
    private final Set<String> readSent = new HashSet<>();
    private int ticks;

    ReceiptSender(LinkService link) {
        this.link = link;
    }

    // сообщение от peerId с этим id расшифровалось у нас
    void delivered(String peerId, String messageId) {
        if (messageId != null) {
            delivered.computeIfAbsent(peerId, key -> new ArrayList<>()).add(messageId);
        }
    }

    // игрок увидел сообщение (чат открыт)
    void read(String peerId, String messageId) {
        if (messageId == null || readSent.contains(messageId)) {
            return;
        }
        if (readSent.size() >= MAX_REMEMBERED) {
            readSent.clear();
        }
        readSent.add(messageId);
        read.computeIfAbsent(peerId, key -> new ArrayList<>()).add(messageId);
    }

    void tick() {
        if (++ticks < FLUSH_TICKS) {
            return;
        }
        ticks = 0;
        // «прочитано» включает «доставлено»: второе на те же сообщения не нужно
        read.forEach((peer, ids) -> {
            List<String> pending = delivered.get(peer);
            if (pending != null) {
                pending.removeAll(ids);
            }
        });
        flush(delivered, false);
        flush(read, true);
    }

    private void flush(Map<String, List<String>> pending, boolean isRead) {
        if (pending.isEmpty()) {
            return;
        }
        pending.forEach((peer, ids) -> {
            for (int from = 0; from < ids.size(); from += MAX_BATCH) {
                link.sendReceipt(peer, List.copyOf(ids.subList(from, Math.min(ids.size(), from + MAX_BATCH))), isRead);
            }
        });
        pending.clear();
    }
}
