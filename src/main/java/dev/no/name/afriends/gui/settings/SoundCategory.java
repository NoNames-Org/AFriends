package dev.no.name.afriends.gui.settings;

import dev.no.name.afriends.api.SettingsCategory;
import dev.no.name.afriends.compat.Compat;
import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.core.SoundSettings;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.media.MicrophoneDevices;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.icon.Icon;
import dev.no.name.afriends.gui.sound.UiSound;
import dev.no.name.afriends.gui.sound.UiSounds;

import java.util.ArrayList;
import java.util.List;

/**
 * Категория «Звук»: звуки интерфейса AFriends, громкость игры (из настроек Minecraft) и выбор микрофона
 * для голосовых. Громкость меняется кликом по полоске, микрофон — кликом по строке (перебор устройств).
 * Своя отрисовка, потому что это не только переключатели. Звук сообщений — в настройках мессенджера
 */
public final class SoundCategory {
    private static final String DEFAULT_MIC = "По умолчанию";
    private static final int TOGGLE_Y = 0;
    private static final int VOLUME_LABEL_Y = 32;
    private static final int VOLUME_TRACK_Y = 46;
    private static final int TRACK_HEIGHT = 7;
    private static final int MIC_LABEL_Y = 72;
    private static final int SWITCH_WIDTH = 22;

    private SoundCategory() {
    }

    public static SettingsCategory create(SoundSettings sound) {
        return new SettingsCategory() {
            @Override
            public String title() {
                return "Звук";
            }

            @Override
            public String subtitle() {
                return "Звуки AFriends, громкость и микрофон";
            }

            @Override
            public Icon icon() {
                return Icon.VOLUME;
            }

            @Override
            public void draw(Gfx g, int x, int top, int width, int mouseX, int mouseY) {
                drawBody(g, sound, x, top, width);
            }

            @Override
            public boolean click(double mouseX, double mouseY, int x, int top, int width) {
                return clickBody(sound, mouseX, mouseY, x, top, width);
            }
        };
    }

    private static void drawBody(Gfx g, SoundSettings sound, int x, int top, int width) {
        // звуки интерфейса
        Draw.text(g, "Звуки интерфейса", x, top + TOGGLE_Y, Theme.TEXT);
        Draw.text(g, Draw.trim("Щелчки кнопок, открытие окон и меню", width - SWITCH_WIDTH - 8), x, top + TOGGLE_Y + 11,
                Theme.TEXT_DIM);
        ToggleList.drawSwitch(g, x + width - SWITCH_WIDTH, top + TOGGLE_Y + 4, sound.uiSounds());

        // громкость игры (общая громкость Minecraft)
        int volume = Math.round(Compat.masterVolume() * 100);
        Draw.text(g, "Громкость игры — " + volume + "%", x, top + VOLUME_LABEL_Y, Theme.TEXT);
        int trackY = top + VOLUME_TRACK_Y;
        Draw.box(g, x, trackY, width, TRACK_HEIGHT, TRACK_HEIGHT / 2f, Theme.CARD, Theme.BORDER_HOVER);
        int filled = Math.round(width * clamp(Compat.masterVolume()));
        if (filled > 0) {
            Draw.roundedRect(g, x, trackY, filled, TRACK_HEIGHT, TRACK_HEIGHT / 2f, Theme.ACCENT);
        }

        // микрофон для голосовых
        Draw.text(g, "Микрофон", x, top + MIC_LABEL_Y, Theme.TEXT);
        Draw.text(g, Draw.trim(currentMicLabel(sound), width - 16), x, top + MIC_LABEL_Y + 11, Theme.TEXT_SOFT);
        Draw.icon(g, Icon.REFRESH, x + width - 12, top + MIC_LABEL_Y + 2, Theme.ICON_SMALL, Theme.TEXT_DIM);
    }

    private static boolean clickBody(SoundSettings sound, double mouseX, double mouseY, int x, int top, int width) {
        // переключатель звуков интерфейса
        if (inside(mouseX, mouseY, x, top + TOGGLE_Y - 2, width, 24)) {
            sound.toggleUiSounds();
            UiSounds.play(UiSound.CLICK);
            return true;
        }
        // клик по полоске громкости — выставить значение
        int trackY = top + VOLUME_TRACK_Y;
        if (inside(mouseX, mouseY, x - 2, trackY - 3, width + 4, TRACK_HEIGHT + 6)) {
            Compat.setMasterVolume(clamp((float) ((mouseX - x) / width)));
            return true;
        }
        // клик по строке микрофона — следующее устройство
        if (inside(mouseX, mouseY, x, top + MIC_LABEL_Y - 2, width, 24)) {
            cycleMicrophone(sound);
            UiSounds.play(UiSound.CLICK);
            return true;
        }
        return false;
    }

    private static void cycleMicrophone(SoundSettings sound) {
        List<String> options = new ArrayList<>();
        options.add("");
        options.addAll(MicrophoneDevices.list());
        int current = Math.max(0, options.indexOf(sound.microphone()));
        String next = options.get((current + 1) % options.size());
        sound.setMicrophone(next);
    }

    private static String currentMicLabel(SoundSettings sound) {
        String name = sound.microphone();
        return name.isBlank() ? DEFAULT_MIC : name;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
