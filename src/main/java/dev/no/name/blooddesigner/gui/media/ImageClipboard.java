package dev.no.name.blooddesigner.gui.media;

import dev.no.name.blooddesigner.BloodDesigner;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * «Копировать картинку»: в буфер обмена системы. В игре AWT без экрана, поэтому, как и «Открыть», через команду системы.
 * PNG и JPG кладутся картинкой; гифка — файлом, иначе при вставке пропадёт анимация. Путь уходит аргументом
 * или переменной окружения, а не внутрь текста команды. Ответ — на потоке игры
 */
public final class ImageClipboard {
    private static final Logger LOGGER = LoggerFactory.getLogger(BloodDesigner.NAME);
    private static final long TIMEOUT_SECONDS = 10;

    private ImageClipboard() {
    }

    public static void copy(Path file, String mime, Consumer<Boolean> done) {
        CompletableFuture.supplyAsync(() -> run(file, mime))
                .whenComplete((ok, error) -> Minecraft.getInstance().execute(() -> done.accept(error == null && ok)));
    }

    private static boolean run(Path file, String mime) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        boolean gif = "image/gif".equals(mime);
        try {
            ProcessBuilder builder;
            if (os.contains("mac")) {
                builder = new ProcessBuilder(mac(gif, mime, file));
            } else if (os.contains("win")) {
                builder = new ProcessBuilder(windows(gif));
                builder.environment().put("BLOODDESIGNER_IMAGE", file.toString());
            } else {
                builder = linux(file, mime);
            }
            // вывод не читаем: xclip и wl-copy остаются в фоне держать буфер и не закрыли бы поток
            Process process = builder.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            return process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException e) {
            LOGGER.warn("{}: картинка не скопировалась", BloodDesigner.NAME, e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static List<String> mac(boolean gif, String mime, Path file) {
        String content = gif ? "(POSIX file (item 1 of argv))"
                : "(read (POSIX file (item 1 of argv)) as " + ("image/png".equals(mime) ? "«class PNGf»" : "JPEG picture") + ")";
        return List.of("osascript", "-e", "on run argv", "-e", "set the clipboard to " + content, "-e", "end run", file.toString());
    }

    private static List<String> windows(boolean gif) {
        String script = "Add-Type -AssemblyName System.Windows.Forms,System.Drawing; " + (gif
                ? "$f = New-Object System.Collections.Specialized.StringCollection; $f.Add($env:BLOODDESIGNER_IMAGE) | Out-Null; "
                + "[System.Windows.Forms.Clipboard]::SetFileDropList($f)"
                : "[System.Windows.Forms.Clipboard]::SetImage([System.Drawing.Image]::FromFile($env:BLOODDESIGNER_IMAGE))");
        return List.of("powershell", "-NoProfile", "-STA", "-Command", script);
    }

    // Wayland — wl-copy (файл на вход), иначе xclip
    private static ProcessBuilder linux(Path file, String mime) {
        if (System.getenv("WAYLAND_DISPLAY") != null) {
            return new ProcessBuilder("wl-copy", "--type", mime).redirectInput(file.toFile());
        }
        return new ProcessBuilder("xclip", "-selection", "clipboard", "-t", mime, "-i", file.toString());
    }
}
