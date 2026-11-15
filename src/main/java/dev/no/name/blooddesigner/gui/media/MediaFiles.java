package dev.no.name.blooddesigner.gui.media;

import dev.no.name.blooddesigner.BloodDesigner;
import dev.no.name.blooddesigner.compat.Compat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Окна системы для картинок: выбрать файл для отправки, сохранить картинку себе, открыть её в просмотрщике.
 * Сами окна — в Compat: в 1.21.11 и 26.1 это TinyFileDialogs, в 26.3 — SDL3. Ответы приходят на поток игры
 */
public final class MediaFiles {
    private static final Logger LOGGER = LoggerFactory.getLogger(BloodDesigner.NAME);
    private static final String[] PATTERNS = {"*.png", "*.jpg", "*.jpeg", "*.gif"};

    private MediaFiles() {
    }

    public static void chooseImage(Consumer<Path> chosen) {
        Compat.chooseFile("Картинка или гифка", PATTERNS, "Картинки (PNG, JPG, GIF)", file -> {
            if (file != null) {
                chosen.accept(file);
            }
        });
    }

    // done — true, если сохранено; отмена окна — ничего не зовёт
    public static void saveAs(Path source, Consumer<Boolean> done) {
        String extension = extension(source);
        String suggested = Path.of(System.getProperty("user.home"), "Downloads",
                "blooddesigner-" + System.currentTimeMillis() + "." + extension).toString();
        Compat.saveFile("Сохранить картинку", suggested, new String[] {"*." + extension}, extension.toUpperCase(Locale.ROOT), target -> {
            if (target == null) {
                return;
            }
            Path path = target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith("." + extension)
                    ? target : target.resolveSibling(target.getFileName() + "." + extension);
            try {
                Files.copy(source, path, StandardCopyOption.REPLACE_EXISTING);
                done.accept(true);
            } catch (IOException e) {
                LOGGER.warn("{}: картинка не сохранилась", BloodDesigner.NAME, e);
                done.accept(false);
            }
        });
    }

    // открыть системным просмотрщиком: в игре AWT без экрана, поэтому через команду системы
    public static void open(Path file) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String[] command = os.contains("mac") ? new String[] {"open", file.toString()}
                : os.contains("win") ? new String[] {"rundll32", "url.dll,FileProtocolHandler", file.toString()}
                : new String[] {"xdg-open", file.toString()};
        try {
            new ProcessBuilder(command).start();
        } catch (IOException e) {
            LOGGER.warn("{}: картинка не открылась в просмотрщике", BloodDesigner.NAME, e);
        }
    }

    public static boolean isImage(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif");
    }

    private static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "png" : name.substring(dot + 1);
    }
}
