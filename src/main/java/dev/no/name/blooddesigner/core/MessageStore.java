package dev.no.name.blooddesigner.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import dev.no.name.blooddesigner.BloodDesigner;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class MessageStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(BloodDesigner.NAME);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final long SAVE_DELAY_MS = 2000;

    private final SocialData social;
    private final Path file;
    private boolean dirty;
    private long lastChange;

    public MessageStore(SocialData social) {
        this.social = social;
        this.file = FabricLoader.getInstance().getConfigDir().resolve(BloodDesigner.MOD_ID).resolve("messages.json");
    }

    public boolean load() {
        if (!Files.exists(file)) {
            return false;
        }
        try {
            social.messagesFromJson(JsonParser.parseString(Files.readString(file)));
            return true;
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Не удалось прочитать переписки из {}", file, e);
            return false;
        }
    }

    public void markDirty() {
        dirty = true;
        lastChange = System.currentTimeMillis();
    }

    public void tick() {
        if (dirty && System.currentTimeMillis() - lastChange >= SAVE_DELAY_MS) {
            save();
        }
    }

    public void save() {
        dirty = false;
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(social.messagesJson()));
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.error("Не удалось сохранить переписки в {}", file, e);
        }
    }
}
