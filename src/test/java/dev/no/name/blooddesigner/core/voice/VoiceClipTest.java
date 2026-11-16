package dev.no.name.blooddesigner.core.voice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceClipTest {
    private static short[] tone(int samples) {
        short[] pcm = new short[samples];
        for (int i = 0; i < samples; i++) {
            pcm[i] = (short) (Math.sin(2 * Math.PI * 440 * i / VoiceClip.SAMPLE_RATE) * 12_000 * Math.min(1, i / 800.0));
        }
        return pcm;
    }

    @Test
    @DisplayName("запись после кодека почти та же: ADPCM теряет немного, но речь узнаётся")
    void roundTrip() {
        short[] pcm = tone(VoiceClip.SAMPLE_RATE * 2 + 1);
        byte[] file = VoiceClip.of(pcm).encode();
        assertTrue(file.length < pcm.length, "в 4 раза меньше PCM");
        VoiceClip back = VoiceClip.decode(file);
        assertNotNull(back);
        assertEquals(pcm.length, back.samples().length);
        double error = 0;
        double signal = 0;
        for (int i = 0; i < pcm.length; i++) {
            error += Math.pow(pcm[i] - back.samples()[i], 2);
            signal += Math.pow(pcm[i], 2);
        }
        assertTrue(10 * Math.log10(signal / error) > 20, "сигнал/шум выше 20 дБ");
        assertEquals(200, back.centiseconds());
        assertArrayEquals(VoiceClip.of(pcm).peaks(), VoiceClip.peaksOf(file));
    }

    @Test
    @DisplayName("чужой или обрезанный файл не раскодируется")
    void rejectsGarbage() {
        assertNull(VoiceClip.decode(new byte[] {1, 2, 3}));
        byte[] file = VoiceClip.of(tone(16_000)).encode();
        byte[] cut = java.util.Arrays.copyOf(file, file.length / 2);
        assertNull(VoiceClip.decode(cut));
        file[0] = 'X';
        assertNull(VoiceClip.decode(file));
    }
}
