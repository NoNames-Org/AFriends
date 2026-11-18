package dev.no.name.afriends.gui.notify;

import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.animation.Easing;
import dev.no.name.afriends.gui.render.Colors;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.icon.Icon;
import dev.no.name.afriends.gui.render.text.TextStyle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Всплывающие уведомления в правом верхнем углу о новых сообщениях. Рисуются нашим рендером ({@link Draw}: шейдерные
 * панели, свой шрифт, значки) в координатах интерфейса, как панель {@code HubRail}. Появляются со сдвигом справа,
 * держатся несколько секунд и плавно гаснут. Один чат занимает одну карточку: повторное сообщение из того же чата
 * обновляет её, а не плодит стопку. Слой HUD регистрирует compat каждой версии и отдаёт сюда {@link Gfx}
 */
public final class MessageToasts {
    private static final MessageToasts INSTANCE = new MessageToasts();

    private static final int MAX = 3;
    private static final long HOLD_MS = 5000;
    private static final long FADE_MS = 400;
    private static final long SLIDE_MS = 260;
    private static final int WIDTH = 150;
    private static final int HEIGHT = 32;
    private static final int MARGIN = 6;
    private static final int GAP = 5;
    private static final int PAD = 7;
    private static final int BADGE = 18;
    private static final int ICON = 10;
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

    /** Вызывается каждый кадр слоем HUD текущей версии; screenWidth и guiScale — интерфейса игры. */
    public synchronized void render(Gfx g, int screenWidth, int guiScale, long now) {
        if (cards.isEmpty()) {
            return;
        }
        cards.removeIf(card -> now - card.shownAt() > HOLD_MS + FADE_MS);
        List<Card> snapshot = new ArrayList<>(cards);
        Draw.setPixelScale(guiScale);
        float y = MARGIN;
        for (Card card : snapshot) {
            long age = now - card.shownAt();
            float appear = Easing.outCubic(clamp01(age / (float) SLIDE_MS));
            float fade = age > HOLD_MS ? clamp01(1f - (age - HOLD_MS) / (float) FADE_MS) : 1f;
            float alpha = Math.min(appear, fade);
            if (alpha > 0f) {
                float x = screenWidth - WIDTH - MARGIN;
                Draw.pushAlpha(alpha);
                Draw.pushOffset(g, (1f - appear) * (WIDTH + MARGIN), 0);
                drawCard(g, card, x, y);
                Draw.popOffset(g);
                Draw.popAlpha();
            }
            // гаснущая карточка уступает место плавно, а не скачком
            y += (HEIGHT + GAP) * fade;
        }
    }

    // карточка как панели мессенджера: тень, градиент, рамка, значок в плашке, заголовок и подпись
    private static void drawCard(Gfx g, Card card, float x, float y) {
        Draw.shadow(g, x, y + 2, WIDTH, HEIGHT, Theme.PANEL_RADIUS, 10, Colors.fade(Theme.SHADOW, 0.6f));
        Draw.gradient(g, x, y, WIDTH, HEIGHT, Theme.PANEL_RADIUS, Theme.PANEL_TOP, Theme.PANEL_BOTTOM);
        Draw.border(g, x, y, WIDTH, HEIGHT, Theme.PANEL_RADIUS, Theme.BORDER);
        float badgeX = x + PAD;
        float badgeY = y + (HEIGHT - BADGE) / 2f;
        Draw.box(g, badgeX, badgeY, BADGE, BADGE, Theme.RADIUS, Theme.ACCENT, Theme.ACCENT);
        Draw.icon(g, Icon.MESSAGE, badgeX + (BADGE - ICON) / 2f, badgeY + (BADGE - ICON) / 2f, ICON, Theme.ACCENT_TEXT);
        float textX = badgeX + BADGE + 7;
        int textWidth = (int) (x + WIDTH - PAD - textX);
        Draw.text(g, Draw.trim(card.title(), textWidth, TextStyle.BODY_MEDIUM), textX, y + 7, Theme.TEXT, TextStyle.BODY_MEDIUM);
        Draw.text(g, Draw.trim(card.subtitle(), textWidth, TextStyle.SMALL), textX, y + 18, Theme.TEXT_SOFT, TextStyle.SMALL);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
