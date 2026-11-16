package dev.no.name.blooddesigner.net.media;

import dev.no.name.blooddesigner.core.MessageImage;
import dev.no.name.blooddesigner.net.tunnel.TunnelError;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;

/**
 * Файл, который игрок выбрал для отправки: проверенный по содержимому (PNG, JPEG или GIF — не по расширению),
 * не больше 5 МБ, с размерами и отпечатком sha256. Бросает TunnelError с кодом, понятным интерфейсу
 */
public record PreparedImage(byte[] bytes, String mime, int width, int height, String sha256) {

    public static PreparedImage read(Path file) {
        try {
            long size = Files.size(file);
            if (size <= 0) {
                throw new TunnelError("image_empty");
            }
            if (size > MessageImage.MAX_SIZE) {
                throw new TunnelError("image_too_big");
            }
            return of(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new TunnelError("image_unreadable");
        }
    }

    // голосовое идёт тем же путём, что и картинка: своё шифрование, сервер видит только шум.
    // width — длительность в сотых секунды, height — заглушка (поле обязано быть положительным)
    public static PreparedImage voice(byte[] bytes, int centiseconds) {
        if (bytes.length > MessageImage.MAX_SIZE) {
            throw new TunnelError("image_too_big");
        }
        return new PreparedImage(bytes, dev.no.name.blooddesigner.core.voice.VoiceClip.MIME, Math.max(1, centiseconds), 1,
                sha256(bytes));
    }

    public static PreparedImage of(byte[] bytes) {
        String mime = mimeOf(bytes);
        if (mime == null) {
            throw new TunnelError("image_format");
        }
        int[] size = dimensions(bytes);
        return new PreparedImage(bytes, mime, size[0], size[1], sha256(bytes));
    }

    // по первым байтам файла: расширение может врать
    static String mimeOf(byte[] data) {
        if (data.length > 8 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return "image/png";
        }
        if (data.length > 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (data.length > 6 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F' && data[3] == '8') {
            return "image/gif";
        }
        return null;
    }

    // размеры из заголовка, без распаковки всей картинки
    private static int[] dimensions(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new TunnelError("image_format");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > 16384 || height > 16384) {
                    throw new TunnelError("image_format");
                }
                return new int[] {width, height};
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new TunnelError("image_format");
        }
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
