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
    private final Map<String, VoiceClip> loaded = new ConcurrentHashMap<>();
    private final Map<String, Boolean> loading = new ConcurrentHashMap<>();
    private final VoicePlayer player;

    public VoiceClips(VoicePlayer player) {
        this.player = player;
    }

    public VoicePlayer player() {
        return player;
    }

    public static String key(MessageImage voice) {
        return voice.sha256();
    }

    // готовая запись или null (тогда загрузка уже идёт)
    public VoiceClip get(MessageImage voice, MessageRoute route) {
        VoiceClip clip = loaded.get(key(voice));
        if (clip == null && loading.putIfAbsent(key(voice), true) == null) {
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
                            loaded.put(key(voice), decoded);
                        }
                        loading.remove(key(voice));
                    });
        }
        return clip;
    }

    // ▶ — играть, а если уже играет эта — остановить
    public void toggle(MessageImage voice, MessageRoute route) {
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
            for (int i = 0; i < 100 && !loaded.containsKey(key(voice)); i++) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            VoiceClip ready = loaded.get(key(voice));
            if (ready != null) {
                Minecraft.getInstance().execute(() -> player.play(key(voice), ready));
            }
        });
    }
}
