package dev.no.name.blooddesigner.gui.friends;

import dev.no.name.blooddesigner.core.MessageImage;
import dev.no.name.blooddesigner.core.MessageRoute;
import dev.no.name.blooddesigner.core.PrivateMessage;
import dev.no.name.blooddesigner.gui.media.MediaFiles;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Картинки и реакции в переписке: прикрепить файл или перетащить его в окно, поставить реакцию,
 * сохранить картинку себе или открыть в просмотрщике. Ошибки — в warn, подсказки — в info (над полем ввода)
 */
final class MediaActions {
    private static final int MAX_DROP = 5;

    private final MessageRoute route;
    private final Consumer<String> warn;
    private final Consumer<String> info;

    MediaActions(MessageRoute route, Consumer<String> warn, Consumer<String> info) {
        this.route = route;
        this.warn = warn;
        this.info = info;
    }

    boolean available(String partner) {
        return route.supportsMedia(partner);
    }

    void attach(String partner) {
        if (!available(partner)) {
            warn.accept("Картинки можно отправлять только друзьям по сети");
            return;
        }
        MediaFiles.chooseImage(file -> send(partner, file));
    }

    // файлы, перетащенные в окно игры: картинки уходят по очереди, остальное пропускаем
    void drop(String partner, List<Path> files) {
        List<Path> images = files.stream().filter(MediaFiles::isImage).limit(MAX_DROP).toList();
        if (images.isEmpty()) {
            warn.accept("Можно перетащить только PNG, JPG или GIF");
            return;
        }
        if (!available(partner)) {
            warn.accept("Картинки можно отправлять только друзьям по сети");
            return;
        }
        images.forEach(file -> send(partner, file));
    }

    void react(String partner, PrivateMessage message, String reaction) {
        route.react(partner, message, reaction, warn);
    }

    void save(MessageImage image) {
        withFile(image, file -> MediaFiles.saveAs(file, saved -> info.accept(saved ? "Картинка сохранена" : "Не получилось сохранить")));
    }

    void open(MessageImage image) {
        withFile(image, MediaFiles::open);
    }

    private void send(String partner, Path file) {
        route.sendImage(partner, file, warn).ifPresentOrElse(warn, () -> info.accept("Картинка отправляется…"));
    }

    private void withFile(MessageImage image, Consumer<Path> action) {
        route.imageFile(image).whenComplete((file, error) -> Minecraft.getInstance().execute(() -> {
            if (error != null) {
                warn.accept("Картинка ещё не загрузилась");
            } else {
                action.accept(file);
            }
        }));
    }
}
