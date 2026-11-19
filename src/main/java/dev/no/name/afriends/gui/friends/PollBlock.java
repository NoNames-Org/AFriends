package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.render.Colors;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.icon.Icon;
import dev.no.name.afriends.gui.render.text.TextStyle;
import dev.no.name.afriends.core.PollView;

import java.util.ArrayList;
import java.util.List;

/**
 * Опрос внутри пузыря: в объявлении или в группе. До голоса — варианты кнопками, после — полоски с процентами и галочка
 * у своего. Клик по другому варианту переголосовывает, по своему — отзывает голос
 */
final class PollBlock {
    static final float MIN_WIDTH = 150;
    private static final int ROW = 13;
    private static final int ROW_GAP = 2;
    private static final int FOOTER = 10;
    private static final float RADIUS = 3;

    // message — сообщение с опросом группы (у опроса объявления null: он и так знает своё объявление)
    record Hit(PollView poll, String message, int option, float x, float y, float width, float height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private PollBlock() {
    }

    static float height(PollView poll) {
        return poll.optionTexts().size() * (ROW + ROW_GAP) + FOOTER;
    }

    static float contentWidth(PollView poll) {
        float widest = 0;
        for (String option : poll.optionTexts()) {
            widest = Math.max(widest, Draw.width(option, TextStyle.SMALL));
        }
        return Math.max(MIN_WIDTH, widest + 40);
    }

    static List<Hit> draw(Gfx g, PollView poll, String message, float x, float y, float width, int textColor) {
        List<Hit> hits = new ArrayList<>();
        boolean voted = poll.voted();
        List<String> options = poll.optionTexts();
        for (int i = 0; i < options.size(); i++) {
            String option = options.get(i);
            float rowY = y + i * (ROW + ROW_GAP);
            boolean chosen = poll.mine() == i;
            Draw.roundedRect(g, x, rowY, width, ROW, RADIUS, Colors.fade(textColor, 0.07f));
            float textX = x + 4;
            if (voted) {
                float fill = width * poll.share(i) / 100f;
                if (fill > 0) {
                    Draw.roundedRect(g, x, rowY, fill, ROW, RADIUS, Colors.fade(Theme.accent(), chosen ? 0.38f : 0.18f));
                }
                String share = poll.share(i) + "%";
                float shareWidth = Draw.width(share, TextStyle.SMALL);
                Draw.text(g, share, x + width - 4 - shareWidth, rowY + 4, Colors.fade(textColor, 0.75f), TextStyle.SMALL);
                if (chosen) {
                    Draw.icon(g, Icon.CHECK, textX, rowY + 3, Theme.ICON_SMALL - 1, textColor);
                    textX += Theme.ICON_SMALL + 2;
                }
                Draw.text(g, Draw.trim(option, (int) (x + width - 8 - shareWidth - textX), TextStyle.SMALL),
                        textX, rowY + 4, textColor, TextStyle.SMALL);
            } else {
                Draw.icon(g, Icon.CIRCLE, textX, rowY + 3, Theme.ICON_SMALL - 1, Colors.fade(textColor, 0.6f));
                textX += Theme.ICON_SMALL + 2;
                Draw.text(g, Draw.trim(option, (int) (x + width - 4 - textX), TextStyle.SMALL), textX, rowY + 4,
                        textColor, TextStyle.SMALL);
            }
            hits.add(new Hit(poll, message, i, x, rowY, width, ROW));
        }
        float footerY = y + options.size() * (ROW + ROW_GAP) + 1;
        String footer = voted ? votes(poll.total()) : "Выбери вариант, чтобы увидеть итоги";
        Draw.text(g, footer, x, footerY, Colors.fade(textColor, 0.6f), TextStyle.SMALL);
        return hits;
    }

    static String votes(int total) {
        int tens = total % 100;
        int ones = total % 10;
        String word = tens >= 11 && tens <= 14 ? "голосов" : ones == 1 ? "голос" : ones >= 2 && ones <= 4 ? "голоса" : "голосов";
        return total + " " + word;
    }
}
