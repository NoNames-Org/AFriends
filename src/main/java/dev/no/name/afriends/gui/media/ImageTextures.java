package dev.no.name.afriends.gui.media;

import com.mojang.blaze3d.platform.NativeImage;
import dev.no.name.afriends.AFriends;
import dev.no.name.afriends.core.MessageImage;
import dev.no.name.afriends.core.MessageRoute;
import net.minecraft.client.Minecraft;
import dev.no.name.afriends.compat.SmoothTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Картинки переписки как текстуры игры. Первый показ запускает загрузку (кеш на диске или скачивание),
 * разбор на кадры идёт в фоне, текстуры создаются на потоке игры. Держим не больше LIMIT картинок:
 * давно не показанные выгружаются из видеопамяти и при надобности соберутся заново из файла
 */
public final class ImageTextures {
    private static final Logger LOGGER = LoggerFactory.getLogger(AFriends.NAME);
    private static final ImageTextures INSTANCE = new ImageTextures();
    private static final int LIMIT = 40;

    public enum State { LOADING, READY, FAILED }

    public static final class Entry {
        private volatile State state = State.LOADING;
        private ImageFrames frames;
        private List<Identifier> textures = List.of();
        private Path file;
        private final long started = System.currentTimeMillis();

        public State state() {
            return state;
        }

        public Path file() {
            return file;
        }

        public int width() {
            return frames == null ? 0 : frames.width();
        }

        public int height() {
            return frames == null ? 0 : frames.height();
        }

        // текстура кадра, который сейчас на экране; гифка идёт с момента первого показа
        public Identifier texture(long now) {
            return textures.isEmpty() ? null : textures.get(frames.frameAt(now - started));
        }
    }

    // порядок доступа: первыми выгружаются давно не показанные
    private final Map<String, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);

    private ImageTextures() {
    }

    public static ImageTextures get() {
        return INSTANCE;
    }

    // зовётся с потока игры при каждом кадре отрисовки
    public Entry entry(MessageImage image, MessageRoute route) {
        return load(image.sha256(), image.animated(), ImageFrames.MAX_SIDE, () -> route.imageFile(image));
    }

    // крупная версия для просмотра во всё окно. Гифка остаётся маленькой: 150 крупных кадров съели бы видеопамять
    public Entry full(MessageImage image, MessageRoute route) {
        if (image.animated() || Math.max(image.width(), image.height()) <= ImageFrames.MAX_SIDE) {
            return entry(image, route);
        }
        return load(image.sha256() + "-full", false, ImageFrames.FULL_SIDE, () -> route.imageFile(image));
    }

    // файл с диска, ещё не отправленный: превью над полем ввода и избранные гифки
    public Entry file(Path file, boolean animated) {
        String key = "file-" + Integer.toHexString(file.toAbsolutePath().toString().hashCode());
        return load(key, animated, ImageFrames.MAX_SIDE, () -> CompletableFuture.completedFuture(file));
    }

    private Entry load(String key, boolean animated, int maxSide,
                       Supplier<CompletableFuture<Path>> source) {
        Entry existing = entries.get(key);
        if (existing != null) {
            return existing;
        }
        Entry entry = new Entry();
        entries.put(key, entry);
        evict();
        source.get()
                .thenApplyAsync(path -> {
                    try {
                        return new Loaded(path, ImageFrames.read(path, animated, maxSide));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                })
                .whenComplete((loaded, error) -> Minecraft.getInstance().execute(() -> {
                    if (error != null) {
                        LOGGER.info("{}: картинка не открылась ({})", AFriends.NAME, error.getMessage());
                        entry.state = State.FAILED;
                    } else if (entries.get(key) == entry) {
                        upload(key, entry, loaded);
                    }
                }));
        return entry;
    }

    private record Loaded(Path file, ImageFrames frames) {
    }

    private void upload(String sha, Entry entry, Loaded loaded) {
        List<Identifier> ids = new ArrayList<>();
        List<BufferedImage> frames = loaded.frames().frames();
        for (int i = 0; i < frames.size(); i++) {
            BufferedImage frame = frames.get(i);
            NativeImage pixels = new NativeImage(frame.getWidth(), frame.getHeight(), false);
            for (int y = 0; y < frame.getHeight(); y++) {
                for (int x = 0; x < frame.getWidth(); x++) {
                    pixels.setPixel(x, y, frame.getRGB(x, y));
                }
            }
            Identifier id = Identifier.fromNamespaceAndPath(AFriends.MOD_ID, "media/" + sha + "/" + i);
            Minecraft.getInstance().getTextureManager().register(id, new SmoothTexture(() -> "AFriends image", pixels));
            ids.add(id);
        }
        entry.file = loaded.file();
        entry.frames = loaded.frames();
        entry.textures = List.copyOf(ids);
        entry.state = State.READY;
    }

    private void evict() {
        while (entries.size() > LIMIT) {
            Map.Entry<String, Entry> oldest = entries.entrySet().iterator().next();
            entries.remove(oldest.getKey());
            for (Identifier id : oldest.getValue().textures) {
                Minecraft.getInstance().getTextureManager().release(id);
            }
        }
    }
}
