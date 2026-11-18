package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.core.MessageImage;
import dev.no.name.afriends.core.MessageRoute;
import dev.no.name.afriends.core.voice.VoiceClip;
import dev.no.name.afriends.gui.render.Colors;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.icon.Icon;
import dev.no.name.afriends.gui.render.text.TextStyle;
import dev.no.name.afriends.gui.media.VoiceClips;
import dev.no.name.afriends.gui.media.VoicePlayer;

/**
 * Голосовое в пузыре: кнопка ▶/❚❚, волна по громкости (проигранная часть ярче) и длительность.
 * Клик по любой части блока — играть или остановить
 */
final class VoiceBlock {
    static final int WIDTH = 140;
    static final int HEIGHT = 24;
    private static final int BUTTON = 16;
    private static final int BARS = 32;

    record Hit(MessageImage voice, float x, float y, float width, float height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private VoiceBlock() {
    }

    static Hit draw(Gfx g, MessageImage voice, MessageRoute route, float x, float y, int textColor) {
        VoiceClip clip = VoiceClips.get(voice, route);
        String key = VoiceClips.key(voice);
        boolean playing = key.equals(VoicePlayer.get().playing());
        float progress = VoicePlayer.get().progress(key);

        Draw.roundedRect(g, x, y, BUTTON, BUTTON, BUTTON / 2f, Colors.fade(textColor, 0.18f));
        Draw.icon(g, playing ? Icon.PAUSE : Icon.PLAY, x + 4, y + 4, BUTTON - 8, textColor);

        float waveX = x + BUTTON + 6;
        float waveWidth = WIDTH - BUTTON - 6;
        float barWidth = waveWidth / BARS;
        for (int i = 0; i < BARS; i++) {
            float level = clip == null ? 0.15f : level(clip.peaks(), i);
            float barHeight = Math.max(2, level * (BUTTON - 2));
            boolean done = playing && (i + 0.5f) / BARS <= progress;
            Draw.roundedRect(g, waveX + i * barWidth, y + (BUTTON - barHeight) / 2f, Math.max(1, barWidth - 1), barHeight, 0.5f,
                    Colors.fade(textColor, done ? 1f : 0.45f));
        }
        int total = voice.voiceCentiseconds() / 100;
        int shown = playing ? Math.round(total * progress) : total;
        Draw.text(g, clock(shown), waveX, y + BUTTON + 2, Colors.fade(textColor, 0.7f), TextStyle.SMALL);
        return new Hit(voice, x, y, WIDTH, BUTTON);
    }

    // 64 уровня из файла сводим к 32 полоскам
    private static float level(byte[] peaks, int bar) {
        int from = bar * peaks.length / BARS;
        int to = Math.max(from + 1, (bar + 1) * peaks.length / BARS);
        int max = 0;
        for (int i = from; i < to; i++) {
            max = Math.max(max, peaks[i] & 0xFF);
        }
        return max / 255f;
    }

    static String clock(int seconds) {
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }
}
