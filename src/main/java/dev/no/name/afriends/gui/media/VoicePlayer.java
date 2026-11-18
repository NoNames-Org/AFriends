package dev.no.name.afriends.gui.media;

import dev.no.name.afriends.AFriends;
import dev.no.name.afriends.core.voice.VoiceClip;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ShortBuffer;

/**
 * Проигрывание голосовых через OpenAL игры: одно за раз, громкость — общая громкость игры.
 * Источник не привязан к миру (относительный, в нуле), поэтому звук не зависит от того, куда смотришь
 */
public final class VoicePlayer {
    private static final Logger LOGGER = LoggerFactory.getLogger(AFriends.NAME);
    private static final VoicePlayer INSTANCE = new VoicePlayer();

    private int source;
    private int buffer;
    private String playing;
    private float duration;

    private VoicePlayer() {
    }

    public static VoicePlayer get() {
        return INSTANCE;
    }

    // что сейчас играет (ключ вложения) или null
    public String playing() {
        tick();
        return playing;
    }

    // доля проигранного 0..1 для этой записи; 0, если играет другая
    public float progress(String key) {
        if (playing == null || !playing.equals(key) || duration <= 0) {
            return 0;
        }
        return Math.min(1, AL10.alGetSourcef(source, AL11.AL_SEC_OFFSET) / duration);
    }

    public void play(String key, VoiceClip clip) {
        stop();
        try {
            ShortBuffer data = BufferUtils.createShortBuffer(clip.samples().length);
            data.put(clip.samples()).flip();
            buffer = AL10.alGenBuffers();
            AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, data, clip.sampleRate());
            source = AL10.alGenSources();
            AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSource3f(source, AL10.AL_POSITION, 0, 0, 0);
            AL10.alSourcef(source, AL10.AL_GAIN, Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MASTER));
            AL10.alSourcePlay(source);
            playing = key;
            duration = clip.samples().length / (float) clip.sampleRate();
        } catch (RuntimeException e) {
            LOGGER.warn("{}: голосовое не проигралось", AFriends.NAME, e);
            stop();
        }
    }

    public void stop() {
        if (source != 0) {
            AL10.alSourceStop(source);
            AL10.alDeleteSources(source);
            source = 0;
        }
        if (buffer != 0) {
            AL10.alDeleteBuffers(buffer);
            buffer = 0;
        }
        playing = null;
        duration = 0;
    }

    // доиграло — освобождаем источник
    private void tick() {
        if (source != 0 && AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_STOPPED) {
            stop();
        }
    }
}
