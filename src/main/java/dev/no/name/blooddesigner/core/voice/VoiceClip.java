package dev.no.name.blooddesigner.core.voice;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Голосовое сообщение: 16 кГц моно, IMA ADPCM — 4 бита на отсчёт, около 8 КБ в секунду.
 * Файл: "BDV1", частота (u32), число отсчётов (u32), 64 уровня громкости для волны и сами данные.
 * Волна хранится в заголовке, чтобы рисовать пузырь, не раскодируя запись
 */
public record VoiceClip(int sampleRate, short[] samples, byte[] peaks) {
    public static final String MIME = "audio/x-bd-voice";
    public static final int SAMPLE_RATE = 16_000;
    public static final int MAX_SECONDS = 60;
    public static final int PEAKS = 64;
    private static final byte[] MAGIC = {'B', 'D', 'V', '1'};
    private static final int HEADER = 4 + 4 + 4 + PEAKS;

    private static final int[] INDEX_TABLE = {-1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8};
    private static final int[] STEP_TABLE = {
            7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45, 50, 55, 60, 66, 73, 80, 88, 97, 107,
            118, 130, 143, 157, 173, 190, 209, 230, 253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724, 796, 876, 963,
            1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358, 5894,
            6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899, 15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794,
            32767};

    public static VoiceClip of(short[] samples) {
        return new VoiceClip(SAMPLE_RATE, samples, peaks(samples));
    }

    // длительность в сотых секунды: так она лежит в описании вложения
    public int centiseconds() {
        return (int) Math.min(Integer.MAX_VALUE, samples.length * 100L / sampleRate);
    }

    public byte[] encode() {
        int count = samples.length;
        ByteBuffer out = ByteBuffer.allocate(HEADER + (count + 1) / 2).order(ByteOrder.BIG_ENDIAN);
        out.put(MAGIC).putInt(sampleRate).putInt(count).put(peaks);
        int predictor = 0;
        int index = 0;
        int pending = -1;
        for (short sample : samples) {
            int step = STEP_TABLE[index];
            int diff = sample - predictor;
            int code = 0;
            if (diff < 0) {
                code = 8;
                diff = -diff;
            }
            // три бита величины: сравнения с шагом, его половиной и четвертью
            int delta = step >> 3;
            if (diff >= step) {
                code |= 4;
                diff -= step;
                delta += step;
            }
            if (diff >= step >> 1) {
                code |= 2;
                diff -= step >> 1;
                delta += step >> 1;
            }
            if (diff >= step >> 2) {
                code |= 1;
                delta += step >> 2;
            }
            predictor = clamp16((code & 8) != 0 ? predictor - delta : predictor + delta);
            index = clampIndex(index + INDEX_TABLE[code]);
            if (pending < 0) {
                pending = code;
            } else {
                out.put((byte) (pending << 4 | code));
                pending = -1;
            }
        }
        if (pending >= 0) {
            out.put((byte) (pending << 4));
        }
        return out.array();
    }

    // null — не голосовое или файл битый
    public static VoiceClip decode(byte[] data) {
        if (data == null || data.length < HEADER) {
            return null;
        }
        ByteBuffer in = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        for (byte expected : MAGIC) {
            if (in.get() != expected) {
                return null;
            }
        }
        int rate = in.getInt();
        int count = in.getInt();
        if (rate < 8_000 || rate > 48_000 || count < 0 || count > rate * (MAX_SECONDS + 1)
                || (long) HEADER + (count + 1) / 2 > data.length) {
            return null;
        }
        byte[] peaks = new byte[PEAKS];
        in.get(peaks);
        short[] samples = new short[count];
        int predictor = 0;
        int index = 0;
        for (int i = 0; i < count; i++) {
            int packed = data[HEADER + i / 2] & 0xFF;
            int code = i % 2 == 0 ? packed >> 4 : packed & 0x0F;
            int step = STEP_TABLE[index];
            int delta = step >> 3;
            if ((code & 4) != 0) {
                delta += step;
            }
            if ((code & 2) != 0) {
                delta += step >> 1;
            }
            if ((code & 1) != 0) {
                delta += step >> 2;
            }
            predictor = clamp16((code & 8) != 0 ? predictor - delta : predictor + delta);
            index = clampIndex(index + INDEX_TABLE[code]);
            samples[i] = (short) predictor;
        }
        return new VoiceClip(rate, samples, peaks);
    }

    // только заголовок: волна и длительность для пузыря, без раскодирования
    public static byte[] peaksOf(byte[] data) {
        if (data == null || data.length < HEADER || data[0] != 'B' || data[1] != 'D' || data[2] != 'V' || data[3] != '1') {
            return null;
        }
        byte[] peaks = new byte[PEAKS];
        System.arraycopy(data, 12, peaks, 0, PEAKS);
        return peaks;
    }

    // громкость по 64 отрезкам, 0..255 с корнем — тихая речь тоже заметна
    static byte[] peaks(short[] samples) {
        byte[] peaks = new byte[PEAKS];
        if (samples.length == 0) {
            return peaks;
        }
        for (int bar = 0; bar < PEAKS; bar++) {
            int from = (int) ((long) samples.length * bar / PEAKS);
            int to = Math.max(from + 1, (int) ((long) samples.length * (bar + 1) / PEAKS));
            int max = 0;
            for (int i = from; i < to && i < samples.length; i++) {
                max = Math.max(max, Math.abs(samples[i]));
            }
            peaks[bar] = (byte) Math.min(255, Math.round(Math.sqrt(max / 32768.0) * 255));
        }
        return peaks;
    }

    private static int clamp16(int value) {
        return Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
    }

    private static int clampIndex(int value) {
        return Math.max(0, Math.min(STEP_TABLE.length - 1, value));
    }
}
