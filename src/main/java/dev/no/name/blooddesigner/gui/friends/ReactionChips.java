package dev.no.name.blooddesigner.gui.friends;

import dev.no.name.blooddesigner.BloodDesigner;
import dev.no.name.blooddesigner.compat.Gfx;
import dev.no.name.blooddesigner.core.PrivateMessage;
import dev.no.name.blooddesigner.gui.Theme;
import dev.no.name.blooddesigner.gui.render.Colors;
import dev.no.name.blooddesigner.gui.render.Draw;
import dev.no.name.blooddesigner.gui.render.text.TextStyle;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Плашки реакций под сообщением, как в Telegram: эмодзи и число, своя подсвечена.
 * В личке у каждого одна реакция, поэтому плашек одна или две
 */
final class ReactionChips {
    static final int ROW = 13;
    private static final int HEIGHT = 11;
    private static final int EMOJI = 8;
    private static final int GAP = 3;
    private static final int TEXTURE = 72;

    record Chip(String reaction, int count, boolean mine) {
    }

    // где на холсте плашка: клик по ней ставит или снимает свою такую же
    record Hit(float x, float y, float width, float height, String reaction, boolean mine) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private ReactionChips() {
    }

    static List<Chip> of(PrivateMessage message) {
        List<Chip> chips = new ArrayList<>();
        String mine = message.reaction();
        String theirs = message.partnerReaction();
        if (mine != null && mine.equals(theirs)) {
            chips.add(new Chip(mine, 2, true));
        } else {
            if (theirs != null) {
                chips.add(new Chip(theirs, 1, false));
            }
            if (mine != null) {
                chips.add(new Chip(mine, 1, true));
            }
        }
        return chips;
    }

    static float width(List<Chip> chips) {
        float total = 0;
        for (Chip chip : chips) {
            total += chipWidth(chip) + GAP;
        }
        return Math.max(0, total - GAP);
    }

    static List<Hit> draw(Gfx g, List<Chip> chips, float x, float y, int textColor) {
        List<Hit> hits = new ArrayList<>();
        for (Chip chip : chips) {
            float width = chipWidth(chip);
            Draw.roundedRect(g, x, y, width, HEIGHT, HEIGHT / 2f,
                    chip.mine() ? Colors.fade(textColor, 0.22f) : Colors.fade(textColor, 0.1f));
            emoji(g, chip.reaction(), x + 3, y + (HEIGHT - EMOJI) / 2f, EMOJI);
            if (chip.count() > 1) {
                Draw.text(g, String.valueOf(chip.count()), x + 3 + EMOJI + 2, y + 2, textColor, TextStyle.SMALL);
            }
            hits.add(new Hit(x, y, width, HEIGHT, chip.reaction(), chip.mine()));
            x += width + GAP;
        }
        return hits;
    }

    static void emoji(Gfx g, String reaction, float x, float y, int size) {
        g.image(texture(reaction), Math.round(x), Math.round(y), size, size, TEXTURE, TEXTURE);
    }

    static Identifier texture(String reaction) {
        return Identifier.fromNamespaceAndPath(BloodDesigner.MOD_ID, "textures/emoji/" + reaction + ".png");
    }

    private static float chipWidth(Chip chip) {
        return 3 + EMOJI + (chip.count() > 1 ? 2 + Draw.width(String.valueOf(chip.count()), TextStyle.SMALL) : 0) + 3;
    }

    static int textColorFor(boolean mine) {
        return mine ? Theme.ACCENT_TEXT : Theme.TEXT;
    }
}
