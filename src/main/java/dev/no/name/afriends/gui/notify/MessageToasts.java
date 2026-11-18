package dev.no.name.afriends.gui.notify;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Всплывающие уведомления в правом верхнем углу о новых сообщениях — как тосты Minecraft, но свои,
 * чтобы одинаково рисоваться на всех версиях через {@link ToastDraw}. Появляются со сдвигом справа,
 * держатся несколько секунд и плавно гаснут. Один чат занимает одну карточку: повторное сообщение
 * из того же чата обновляет её, а не плодит стопку. Регистрируется слой HUD в compat каждой версии
 */
public final class MessageToasts {
    private static final MessageToasts INSTANCE = new MessageToasts();

    private static final int MAX = 3;
    private static final long HOLD_MS = 5000;
    private static final long FADE_MS = 400;
    private static final long SLIDE_MS = 220;
    private static final int WIDTH = 172;
    private static final int MARGIN = 6;
    private static final int GAP = 6;
    private static final int PAD = 9;
    private static final int LINE = 10;
    private static final int ACCENT = 0x5B8DEF;
    private static final int BG = 0x1C1C1E;
    private static final int TITLE = 0xFFFFFF;
    private static final int SUBTITLE = 0xB3B3B8;
    private static final String SUBTITLE_TEXT = "Новое сообщение";

    private final Deque<Card> cards = new ArrayDeque<>();

    private MessageToasts() {
    }

    public static MessageToasts get() {
        return INSTANCE;
    }

    private record Card(String title, String subtitle, long shownAt) {
    }

    /** Показать уведомление о чате с готовым заголовком (ник, название группы, «AFriends»). */
    public synchronized void push(String title) {
        String name = title == null || title.isBlank() ? "Сообщение" : title;
        long now = System.currentTimeMillis();
        cards.removeIf(card -> card.title().equals(name));
        cards.addFirst(new Card(name, SUBTITLE_TEXT, now));
        while (cards.size() > MAX) {
            cards.removeLast();
        }
    }

    /** Вызывается каждый кадр слоем HUD текущей версии. */
    public synchronized void render(ToastDraw draw, long now) {
        if (cards.isEmpty()) {
            return;
        }
        cards.removeIf(card -> now - card.shownAt() > HOLD_MS + FADE_MS);
        List<Card> snapshot = new ArrayList<>(cards);
        int height = PAD * 2 + LINE * 2 + 2;
        int y = MARGIN;
        for (Card card : snapshot) {
            long age = now - card.shownAt();
            float appear = clamp01(age / (float) SLIDE_MS);
            float fade = age > HOLD_MS ? clamp01(1f - (age - HOLD_MS) / (float) FADE_MS) : 1f;
            float alpha = Math.min(appear, fade);
            if (alpha <= 0f) {
                continue;
            }
            int slide = Math.round((1f - appear) * (WIDTH + MARGIN));
            int x = draw.screenWidth() - WIDTH - MARGIN + slide;
            int a = Math.round(alpha * 255f);
            draw.box(x, y, WIDTH, height, argb((int) (a * 0.9f), BG));
            draw.box(x, y, 3, height, argb(a, ACCENT));
            int textX = x + PAD;
            int inner = WIDTH - PAD * 2;
            draw.line(trim(draw, card.title(), inner), textX, y + PAD, argb(a, TITLE));
            draw.line(trim(draw, card.subtitle(), inner), textX, y + PAD + LINE + 2, argb(a, SUBTITLE));
            y += height + GAP;
        }
    }

    private static int argb(int alpha, int rgb) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static String trim(ToastDraw draw, String text, int maxWidth) {
        if (draw.textWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        int limit = maxWidth - draw.textWidth(ellipsis);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (draw.textWidth(out.toString() + text.charAt(i)) > limit) {
                break;
            }
            out.append(text.charAt(i));
        }
        return out + ellipsis;
    }
}
