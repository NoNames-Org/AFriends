package dev.no.name.blooddesigner.net.media;

import dev.no.name.blooddesigner.net.tunnel.TunnelError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MediaTest {
    @Test
    @DisplayName("вложение расшифровывается своим ключом, а подменённое или чужим ключом — нет")
    void crypto() throws GeneralSecurityException {
        byte[] key = MediaCrypto.newKey();
        byte[] plain = "картинка".getBytes();
        byte[] sealed = MediaCrypto.seal(key, plain);
        assertArrayEquals(plain, MediaCrypto.open(key, sealed));
        sealed[sealed.length - 1] ^= 1;
        assertThrows(GeneralSecurityException.class, () -> MediaCrypto.open(key, sealed));
        assertThrows(GeneralSecurityException.class, () -> MediaCrypto.open(MediaCrypto.newKey(), MediaCrypto.seal(key, plain)));
    }

    @Test
    @DisplayName("PNG узнаётся по содержимому, размеры читаются из заголовка")
    void png() throws IOException {
        PreparedImage image = PreparedImage.of(encode(new BufferedImage(40, 25, BufferedImage.TYPE_INT_ARGB), "png"));
        assertEquals("image/png", image.mime());
        assertEquals(40, image.width());
        assertEquals(25, image.height());
        assertEquals(64, image.sha256().length());
    }

    @Test
    @DisplayName("не картинка — отказ с понятным кодом")
    void notImage() {
        TunnelError error = assertThrows(TunnelError.class, () -> PreparedImage.of("<html>".getBytes()));
        assertEquals("image_format", error.code());
    }

    static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }
}
