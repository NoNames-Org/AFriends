package dev.no.name.blooddesigner.gui.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.no.name.blooddesigner.BloodDesigner;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Избранные гифки: список в config/blooddesigner/gif-favorites.json, сами файлы — рядом в favorites/.
 * Хранятся у себя, а не ссылкой на сервер: вложения на сервере живут 30 дней, а избранное — пока не уберёшь.
 * Свежие сверху. Только поток игры
 */
public final class GifFavorites {
    private static final Logger LOGGER = LoggerFactory.getLogger(BloodDesigner.NAME);
    private static final GifFavorites INSTANCE = new GifFavorites();
    private static final int LIMIT = 200;
    private static final Pattern SHA = Pattern.compile("[0-9a-f]{64}");

    public record Gif(String sha256, Path file) {
    }

    private final Path dir;
    private final Path listFile;
    private List<String> shas;

    private GifFavorites() {
        Path root = FabricLoader.getInstance().getConfigDir().resolve(BloodDesigner.MOD_ID);
        dir = root.resolve("favorites");
        listFile = root.resolve("gif-favorites.json");
    }

    public static GifFavorites get() {
        return INSTANCE;
    }

    public List<Gif> all() {
        return shas().stream().map(sha -> new Gif(sha, dir.resolve(sha + ".gif"))).filter(gif -> Files.exists(gif.file())).toList();
    }

    public boolean contains(String sha256) {
        return shas().contains(sha256);
    }

    // гифка из переписки: файл уже расшифрован в кеше, копируем его к себе
    public boolean add(String sha256, Path source) {
        if (!SHA.matcher(sha256).matches() || contains(sha256)) {
            return false;
        }
        try {
            Files.createDirectories(dir);
            Files.copy(source, dir.resolve(sha256 + ".gif"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("{}: гифка не сохранилась в избранное", BloodDesigner.NAME, e);
            return false;
        }
        shas().add(0, sha256);
        while (shas().size() > LIMIT) {
            remove(shas().get(shas().size() - 1));
        }
        save();
        return true;
    }

    public void remove(String sha256) {
        if (shas().remove(sha256)) {
            try {
                Files.deleteIfExists(dir.resolve(sha256 + ".gif"));
            } catch (IOException e) {
                LOGGER.debug("{}: файл гифки не удалился", BloodDesigner.NAME, e);
            }
            save();
        }
    }

    private List<String> shas() {
        if (shas == null) {
            shas = new ArrayList<>();
            try {
                if (Files.exists(listFile)) {
                    for (JsonElement element : JsonParser.parseString(Files.readString(listFile)).getAsJsonObject().getAsJsonArray("gifs")) {
                        String sha = element.getAsString();
                        if (SHA.matcher(sha).matches() && !shas.contains(sha)) {
                            shas.add(sha);
                        }
                    }
                }
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("{}: список избранных гифок не прочитался", BloodDesigner.NAME, e);
            }
        }
        return shas;
    }

    private void save() {
        JsonArray array = new JsonArray();
        shas().forEach(array::add);
        JsonObject json = new JsonObject();
        json.add("gifs", array);
        try {
            Files.createDirectories(listFile.getParent());
            Files.writeString(listFile, json.toString());
        } catch (IOException e) {
            LOGGER.warn("{}: список избранных гифок не сохранился", BloodDesigner.NAME, e);
        }
    }
}
