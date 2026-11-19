package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.core.MessageImage;
import dev.no.name.afriends.core.MessageReply;
import dev.no.name.afriends.core.MessageRoute;
import dev.no.name.afriends.core.PrivateMessage;
import dev.no.name.afriends.gui.media.GifFavorites;
import dev.no.name.afriends.gui.media.ImageClipboard;
import dev.no.name.afriends.gui.media.MediaFiles;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Картинки и реакции в переписке: выбрать файл или перетащить его в окно (он встаёт над полем ввода и уходит
 * вместе с подписью), отправить избранную гифку, поставить реакцию, сохранить картинку себе, открыть её,
 * добавить гифку в избранное. Ошибки — в warn, подсказки — в info (над полем ввода)
 */
final class MediaActions {
    private final MessageRoute route;
    private final GifFavorites favorites;
    private final Consumer<String> warn;
    private final Consumer<String> info;

    MediaActions(MessageRoute route, GifFavorites favorites, Consumer<String> warn, Consumer<String> info) {
        this.route = route;
        this.favorites = favorites;
        this.warn = warn;
        this.info = info;
    }

    boolean available(String partner) {
        return route.supportsMedia(partner);
    }

    // окно выбора файла; выбранный уходит в chosen (его покажут над полем ввода)
    void choose(String partner, Consumer<Path> chosen) {
        if (!available(partner)) {
            warn.accept("Картинки можно отправлять только друзьям по сети");
            return;
        }
        MediaFiles.chooseImage(chosen);
    }

    // перетащили файлы в окно игры: первая картинка — к сообщению; null — картинок нет или нельзя
    Path dropped(String partner, List<Path> files) {
        List<Path> images = files.stream().filter(MediaFiles::isImage).toList();
        if (images.isEmpty()) {
            warn.accept("Можно перетащить только PNG, JPG или GIF");
            return null;
        }
        if (!available(partner)) {
            warn.accept("Картинки можно отправлять только друзьям по сети");
            return null;
        }
        if (images.size() > 1) {
            info.accept("Картинки отправляются по одной — взята первая");
        }
        return images.get(0);
    }

    // картинка с подписью; false — сразу не ушла (причина уже в warn)
    boolean send(String partner, Path file, String caption) {
        return send(partner, file, caption, null);
    }

    // то же, ответом на сообщение reply
    boolean send(String partner, Path file, String caption, MessageReply reply) {
        return route.sendImage(partner, file, caption, null, reply, warn).map(problem -> {
            warn.accept(problem);
            return false;
        }).orElseGet(() -> {
            info.accept("Картинка отправляется…");
            return true;
        });
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

    // в буфер обмена системы: картинкой, гифка — файлом
    void copy(MessageImage image) {
        withFile(image, file -> ImageClipboard.copy(file, image.mime(),
                ok -> info.accept(ok ? (image.animated() ? "Гифка скопирована файлом" : "Картинка скопирована")
                        : "Не получилось скопировать")));
    }

    boolean isFavorite(MessageImage image) {
        return favorites.contains(image.sha256());
    }

    // гифка из переписки — в избранное или обратно
    void toggleFavorite(MessageImage image) {
        if (favorites.contains(image.sha256())) {
            favorites.remove(image.sha256());
            info.accept("Гифка убрана из избранного");
            return;
        }
        withFile(image, file -> info.accept(favorites.add(image.sha256(), file) ? "Гифка в избранном" : "Не получилось добавить"));
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
