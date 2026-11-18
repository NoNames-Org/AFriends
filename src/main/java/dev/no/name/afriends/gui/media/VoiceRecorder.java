package dev.no.name.afriends.gui.media;

import dev.no.name.afriends.AFriends;
import dev.no.name.afriends.core.voice.VoiceClip;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ShortBuffer;

/**
 * Запись голосового с микрофона по умолчанию через OpenAL игры: 16 кГц моно. Отсчёты забираются
 * каждый кадр (poll), на минуте запись останавливается сама. На macOS первый раз система спросит
 * доступ к микрофону для лаунчера
 */
public final class VoiceRecorder {
    private static final Logger LOGGER = LoggerFactory.getLogger(AFriends.NAME);
    private static final int MAX_SAMPLES = VoiceClip.SAMPLE_RATE * VoiceClip.MAX_SECONDS;
    // буфер устройства на полсекунды: кадры редко бывают реже
    private static final int DEVICE_BUFFER = VoiceClip.SAMPLE_RATE / 2;
    private static final int MIN_SAMPLES = VoiceClip.SAMPLE_RATE / 2;

    private long device;
    private short[] recorded = new short[0];
    private int length;
    private final ShortBuffer chunk = BufferUtils.createShortBuffer(DEVICE_BUFFER);
    private int level;

    public boolean isRecording() {
        return device != 0;
    }

    // false — микрофона нет или доступ запрещён
    public boolean start() {
        if (device != 0) {
            return true;
        }
        try {
            device = ALC11.alcCaptureOpenDevice((CharSequence) null, VoiceClip.SAMPLE_RATE, AL10.AL_FORMAT_MONO16, DEVICE_BUFFER);
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            LOGGER.warn("{}: микрофон не открылся", AFriends.NAME, e);
            device = 0;
        }
        if (device == 0) {
            return false;
        }
        recorded = new short[VoiceClip.SAMPLE_RATE * 4];
        length = 0;
        level = 0;
        ALC11.alcCaptureStart(device);
        return true;
    }

    // раз в кадр: забрать накопленное. true — достигнут предел минуты, пора отправлять
    public boolean poll() {
        if (device == 0) {
            return false;
        }
        int available = ALC10.alcGetInteger(device, ALC11.ALC_CAPTURE_SAMPLES);
        while (available > 0 && length < MAX_SAMPLES) {
            int take = Math.min(Math.min(available, DEVICE_BUFFER), MAX_SAMPLES - length);
            chunk.clear();
            ALC11.alcCaptureSamples(device, chunk, take);
            if (length + take > recorded.length) {
                short[] grown = new short[Math.min(MAX_SAMPLES, Math.max(recorded.length * 2, length + take))];
                System.arraycopy(recorded, 0, grown, 0, length);
                recorded = grown;
            }
            int peak = 0;
            for (int i = 0; i < take; i++) {
                short sample = chunk.get(i);
                recorded[length + i] = sample;
                peak = Math.max(peak, Math.abs(sample));
            }
            level = peak;
            length += take;
            available -= take;
        }
        return length >= MAX_SAMPLES;
    }

    public int seconds() {
        return length / VoiceClip.SAMPLE_RATE;
    }

    // громкость последних отсчётов 0..1 — для мигающей точки записи
    public float level() {
        return (float) Math.sqrt(level / 32768.0);
    }

    // остановить; null — запись слишком короткая (меньше полсекунды) или её не было
    public VoiceClip stop() {
        if (device == 0) {
            return null;
        }
        poll();
        ALC11.alcCaptureStop(device);
        ALC11.alcCaptureCloseDevice(device);
        device = 0;
        if (length < MIN_SAMPLES) {
            return null;
        }
        short[] samples = new short[length];
        System.arraycopy(recorded, 0, samples, 0, length);
        return VoiceClip.of(samples);
    }

    public void cancel() {
        if (device != 0) {
            ALC11.alcCaptureStop(device);
            ALC11.alcCaptureCloseDevice(device);
            device = 0;
        }
        length = 0;
    }
}
