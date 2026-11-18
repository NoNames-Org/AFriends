package dev.no.name.afriends.gui.settings;

import dev.no.name.afriends.api.SettingsCategory.Toggle;
import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.sound.UiSound;
import dev.no.name.afriends.gui.sound.UiSounds;

import java.util.List;

/** Список переключателей в настройках: название, пояснение и тумблер справа. Клик по строке — переключить. */
public final class ToggleList {
    private static final int ROW_HEIGHT = 28;
    private static final int SWITCH_WIDTH = 22;
    private static final int SWITCH_HEIGHT = 11;
    private static final int TEXT_GAP = 8;

    private final List<Toggle> toggles;
    private final String note;

    public ToggleList(List<Toggle> toggles, String note) {
        this.toggles = List.copyOf(toggles);
        this.note = note == null ? "" : note;
    }

    public void draw(Gfx g, int x, int top, int width) {
        int textWidth = width - SWITCH_WIDTH - TEXT_GAP;
        for (int i = 0; i < toggles.size(); i++) {
            Toggle toggle = toggles.get(i);
            int y = rowY(top, i);
            Draw.text(g, Draw.trim(toggle.title(), textWidth), x, y, Theme.TEXT);
            Draw.text(g, Draw.trim(toggle.description(), textWidth), x, y + 11, Theme.TEXT_DIM);
            drawSwitch(g, x + width - SWITCH_WIDTH, y + 4, toggle.enabled().getAsBoolean());
        }
        if (!note.isEmpty()) {
            Draw.text(g, Draw.trim(note, width), x, rowY(top, toggles.size()) + 2, Theme.TEXT_DIM);
        }
    }

    public boolean click(double mouseX, double mouseY, int x, int top, int width) {
        for (int i = 0; i < toggles.size(); i++) {
            int y = rowY(top, i);
            if (mouseX >= x && mouseX < x + width && mouseY >= y - 2 && mouseY < y + ROW_HEIGHT - 4) {
                toggles.get(i).toggle().run();
                UiSounds.play(UiSound.CLICK);
                return true;
            }
        }
        return false;
    }

    private static int rowY(int top, int index) {
        return top + index * ROW_HEIGHT;
    }

    public static void drawSwitch(Gfx g, float x, float y, boolean on) {
        float radius = SWITCH_HEIGHT / 2f;
        Draw.box(g, x, y, SWITCH_WIDTH, SWITCH_HEIGHT, radius, on ? Theme.ACCENT : Theme.CARD, on ? Theme.ACCENT : Theme.BORDER_HOVER);
        float knob = SWITCH_HEIGHT - 4;
        float knobX = on ? x + SWITCH_WIDTH - knob - 2 : x + 2;
        Draw.roundedRect(g, knobX, y + 2, knob, knob, knob / 2f, on ? Theme.ACCENT_TEXT : Theme.TEXT_SOFT);
    }
}
