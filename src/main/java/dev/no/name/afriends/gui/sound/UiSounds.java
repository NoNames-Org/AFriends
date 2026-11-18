package dev.no.name.afriends.gui.sound;

import dev.no.name.afriends.gui.animation.Motion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

import java.util.Random;

public final class UiSounds {
    private static final long MIN_GAP = 35;
    private static final long[] lastPlayed = new long[UiSound.values().length];
    private static final Random RANDOM = new Random();

    private static volatile boolean enabled = true;

    private UiSounds() {
    }

    /** Звуки интерфейса AFriends: щелчки, открытие меню. Звук новых сообщений настраивается отдельно. */
    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void play(UiSound sound) {
        play(sound, 1);
    }

    public static void play(UiSound sound, float pitch) {
        if (!enabled && sound != UiSound.MESSAGE) {
            return;
        }
        long now = Motion.now();

        if (now - lastPlayed[sound.ordinal()] < MIN_GAP) {
            return;
        }
        lastPlayed[sound.ordinal()] = now;
        float clamped = Math.max(0.5f, Math.min(2, pitch));
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(sound.getEvent(), clamped, sound.getVolume()));
    }

    public static void playVaried(UiSound sound, float spread) {
        play(sound, 1 + (RANDOM.nextFloat() * 2 - 1) * spread);
    }
}
