package dev.no.name.afriends.net.media;

import dev.no.name.afriends.AFriends;
import dev.no.name.afriends.core.MessageImage;
import dev.no.name.afriends.core.voice.VoiceClip;
import dev.no.name.afriends.net.crypto.Bytes;
import dev.no.name.afriends.net.tunnel.TunnelError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Расшифрованные картинки переписки на диске: config/afriends/media/<sha256>.<расширение>.
 * Своё отправленное кладётся сразу, чужое скачивается при первом показе, проверяется по sha256 и больше не качается
 */
public final class MediaCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(AFriends.NAME);

    private final Path dir;
    private final BlobClient blobs;
    private final Map<String, CompletableFuture<Path>> loading = new ConcurrentHashMap<>();

    public MediaCache(Path dir, BlobClient blobs) {
        this.dir = dir;
        this.blobs = blobs;
    }

    public Path path(MessageImage image) {
        return dir.resolve(image.sha256() + "." + image.extension());
    }

    // картинка только для себя (в «Избранном»): в кеше по отпечатку, на сервер не уходит
    public MessageImage keepLocal(PreparedImage prepared) {
        byte[] id = new byte[16];
        new java.security.SecureRandom().nextBytes(id);
        MessageImage image = new MessageImage(HexFormat.of().formatHex(id), "", prepared.mime(), prepared.width(),
                prepared.height(), prepared.bytes().length, prepared.sha256());
        store(image, prepared.bytes());
        return image;
    }

    // своё только что отправленное: показываем без скачивания
    public void store(MessageImage image, byte[] plain) {
        try {
            write(path(image), plain);
        } catch (IOException e) {
            LOGGER.warn("{}: картинка не сохранилась в кеш", AFriends.NAME, e);
        }
    }

    // файл картинки на диске: из кеша или скачанный и расшифрованный; одна загрузка на картинку
    public CompletableFuture<Path> file(MessageImage image) {
        Path target = path(image);
        if (Files.exists(target)) {
            return CompletableFuture.completedFuture(target);
        }
        // картинка портала открытая, от друга — зашифрована его ключом
        return loading.computeIfAbsent(image.sha256(), sha -> (image.isPublic() ? blobs.downloadMedia(image.blob())
                : blobs.download(image.blob())).thenApply(sealed -> {
            try {
                byte[] plain = image.isPublic() ? sealed : MediaCrypto.open(Bytes.fromBase64(image.key()), sealed);
                // голосовое — не картинка: у него свой заголовок BDV1
                boolean format = image.voice() ? VoiceClip.peaksOf(plain) != null
                        : PreparedImage.mimeOf(plain) != null;
                if (!PreparedImage.sha256(plain).equals(image.sha256()) || !format) {
                    throw new TunnelError("image_corrupt");
                }
                write(target, plain);
                return target;
            } catch (GeneralSecurityException | IllegalArgumentException e) {
                throw new TunnelError("image_corrupt");
            } catch (IOException e) {
                throw new TunnelError("image_unreadable");
            }
        }).whenComplete((path, error) -> loading.remove(image.sha256())));
    }

    private void write(Path target, byte[] plain) throws IOException {
        Files.createDirectories(dir);
        Path temp = target.resolveSibling(target.getFileName() + ".part");
        Files.write(temp, plain);
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
