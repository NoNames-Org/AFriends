package dev.no.name.afriends.gui.media;

import dev.no.name.afriends.core.MessageImage;
import dev.no.name.afriends.core.MessageRoute;
import dev.no.name.afriends.core.voice.VoiceClip;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Голосовые на экране: файл качается и расшифровывается тем же кешем, что картинки, раскодируется
 * не на потоке игры. Пока не готово — пузырь рисует ровную волну
 */
public final class VoiceClips {
    private static final Map<String, VoiceClip> LOADED = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> LOADING = new ConcurrentHashMap<>();

    private VoiceClips() {
    }

    public static String key(MessageImage voice) {
        return voice.sha256();
    }

    // готовая запись или null (тогда загрузка уже идёт)
    public static VoiceClip get(MessageImage voice, MessageRoute route) {
        VoiceClip clip = LOADED.get(key(voice));
        if (clip == null && LOADING.putIfAbsent(key(voice), true) == null) {
            route.imageFile(voice)
                    .thenApplyAsync(path -> {
                        try {
                            return VoiceClip.decode(Files.readAllBytes(path));
                        } catch (java.io.IOException e) {
                            return null;
                        }
                    })
                    .whenComplete((decoded, error) -> {
                        if (decoded != null) {
                            LOADED.put(key(voice), decoded);
                        }
                        LOADING.remove(key(voice));
                    });
        }
        return clip;
    }

    // ▶ — играть, а если уже играет эта — остановить
    public static void toggle(MessageImage voice, MessageRoute route) {
        VoicePlayer player = VoicePlayer.get();
        if (key(voice).equals(player.playing())) {
            player.stop();
            return;
        }
        VoiceClip clip = get(voice, route);
        if (clip != null) {
            player.play(key(voice), clip);
            return;
        }
        // ещё качается: сыграем, как только раскодируется
        CompletableFuture.runAsync(() -> {
            for (int i = 0; i < 100 && !LOADED.containsKey(key(voice)); i++) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            VoiceClip ready = LOADED.get(key(voice));
            if (ready != null) {
                Minecraft.getInstance().execute(() -> player.play(key(voice), ready));
            }
        });
    }
}
