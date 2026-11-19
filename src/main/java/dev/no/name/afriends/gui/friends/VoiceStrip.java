package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.compat.Gfx;
import dev.no.name.afriends.core.voice.VoiceClip;
import dev.no.name.afriends.gui.Theme;
import dev.no.name.afriends.gui.render.Colors;
import dev.no.name.afriends.gui.render.Draw;
import dev.no.name.afriends.gui.render.text.TextStyle;
import dev.no.name.afriends.gui.media.VoiceRecorder;

import java.util.function.Consumer;

/**
 * Запись голосового поверх поля ввода: красная точка дышит от громкости, таймер и подсказка.
 * Микрофон ещё раз, Enter или «Отправить» — отправить; Esc — отменить. На минуте уходит само
 */
final class VoiceStrip {
    private final VoiceRecorder recorder = new VoiceRecorder();
    private final Consumer<VoiceClip> send;
    private final Consumer<String> warn;

    VoiceStrip(Consumer<VoiceClip> send, Consumer<String> warn) {
        this.send = send;
        this.warn = warn;
    }

    boolean isRecording() {
        return recorder.isRecording();
    }

    // микрофон: не пишем — начать, пишем — остановить и отправить
    void toggle() {
        if (recorder.isRecording()) {
            finish();
        } else if (!recorder.start()) {
            warn.accept("Микрофон не открылся: проверь, что он есть и что у игры есть к нему доступ");
        }
    }

    void finish() {
        VoiceClip clip = recorder.stop();
        if (clip == null) {
            warn.accept("Слишком коротко — держи запись хотя бы полсекунды");
            return;
        }
        send.accept(clip);
    }

    void cancel() {
        recorder.cancel();
    }

    // раз в кадр, пока пишем
    void tick() {
        if (recorder.isRecording() && recorder.poll()) {
            finish();
        }
    }

    void draw(Gfx g, float x, float y, float width, float height) {
        if (!recorder.isRecording()) {
            return;
        }
        Draw.box(g, x, y, width, height, Theme.RADIUS, Theme.card(), Theme.DANGER);
        float dot = 4 + recorder.level() * 4;
        float dotX = x + 8;
        Draw.roundedRect(g, dotX - dot / 2 + 3, y + height / 2f - dot / 2, dot, dot, dot / 2, Theme.DANGER);
        String time = VoiceBlock.clock(recorder.seconds()) + " / " + VoiceBlock.clock(VoiceClip.MAX_SECONDS);
        Draw.text(g, time, x + 18, y + (height - 7) / 2f, Theme.text(), TextStyle.BODY);
        String hint = "Enter — отправить · Esc — отмена";
        Draw.text(g, hint, x + width - 6 - Draw.width(hint, TextStyle.SMALL), y + (height - 6) / 2f,
                Colors.fade(Theme.textDim(), 0.9f), TextStyle.SMALL);
    }
}
