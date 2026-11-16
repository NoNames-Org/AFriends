package dev.no.name.blooddesigner.gui.settings;

import dev.no.name.blooddesigner.core.NotifySettings;
import dev.no.name.blooddesigner.compat.Gfx;
import dev.no.name.blooddesigner.core.FeatureToggles;
import dev.no.name.blooddesigner.core.FeatureToggles.Feature;
import dev.no.name.blooddesigner.gui.Theme;
import dev.no.name.blooddesigner.gui.render.Draw;
import dev.no.name.blooddesigner.gui.sound.UiSound;
import dev.no.name.blooddesigner.gui.sound.UiSounds;

// раздел «Меню» в настройках: какие меню мода включены; оформлен как раздел «Сеть»
final class MenusSection {
    private static final int ROW_HEIGHT = 28;
    private static final int SWITCH_WIDTH = 22;
    private static final int SWITCH_HEIGHT = 11;
    private static final String NOTE = "Выключенное меню пропадает сразу, кнопки в чате — при следующем открытии чата.";

    private static final String SOUND_TITLE = "Звук сообщений";
    private static final String SOUND_TEXT = "Звук нового сообщения; у отдельного чата — в его меню";

    private final Runnable onChange;
    private final NotifySettings notify;

    MenusSection(Runnable onChange, NotifySettings notify) {
        this.onChange = onChange;
        this.notify = notify;
    }

    void draw(Gfx g, int x, int top, int width) {
        Feature[] features = Feature.values();
        for (int i = 0; i < features.length; i++) {
            Feature feature = features[i];
            int y = rowY(top, i);
            Draw.text(g, Draw.trim(feature.title(), width - SWITCH_WIDTH - 8), x, y, Theme.TEXT);
            Draw.text(g, Draw.trim(feature.description(), width - SWITCH_WIDTH - 8), x, y + 11, Theme.TEXT_DIM);
            drawSwitch(g, x + width - SWITCH_WIDTH, y + 4, FeatureToggles.enabled(feature));
        }
        int soundY = rowY(top, features.length);
        Draw.text(g, Draw.trim(SOUND_TITLE, width - SWITCH_WIDTH - 8), x, soundY, Theme.TEXT);
        Draw.text(g, Draw.trim(SOUND_TEXT, width - SWITCH_WIDTH - 8), x, soundY + 11, Theme.TEXT_DIM);
        drawSwitch(g, x + width - SWITCH_WIDTH, soundY + 4, notify.soundEnabled());
        Draw.text(g, Draw.trim(NOTE, width), x, rowY(top, features.length + 1) + 2, Theme.TEXT_DIM);
    }

    boolean click(double mouseX, double mouseY, int x, int top, int width) {
        Feature[] features = Feature.values();
        for (int i = 0; i < features.length; i++) {
            int y = rowY(top, i);
            if (mouseX >= x && mouseX < x + width && mouseY >= y - 2 && mouseY < y + ROW_HEIGHT - 4) {
                FeatureToggles.toggle(features[i]);
                UiSounds.play(UiSound.CLICK);
                onChange.run();
                return true;
            }
        }
        int soundY = rowY(top, features.length);
        if (mouseX >= x && mouseX < x + width && mouseY >= soundY - 2 && mouseY < soundY + ROW_HEIGHT - 4) {
            notify.toggleSound();
            UiSounds.play(notify.soundEnabled() ? UiSound.MESSAGE : UiSound.CLICK);
            return true;
        }
        return false;
    }

    private static int rowY(int top, int index) {
        return top + index * ROW_HEIGHT;
    }

    private static void drawSwitch(Gfx g, float x, float y, boolean on) {
        float radius = SWITCH_HEIGHT / 2f;
        Draw.box(g, x, y, SWITCH_WIDTH, SWITCH_HEIGHT, radius, on ? Theme.ACCENT : Theme.CARD, on ? Theme.ACCENT : Theme.BORDER_HOVER);
        float knob = SWITCH_HEIGHT - 4;
        float knobX = on ? x + SWITCH_WIDTH - knob - 2 : x + 2;
        Draw.roundedRect(g, knobX, y + 2, knob, knob, knob / 2f, on ? Theme.ACCENT_TEXT : Theme.TEXT_SOFT);
    }
}
