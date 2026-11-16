package dev.no.name.blooddesigner.gui.friends;

import dev.no.name.blooddesigner.core.MessageContent;
import dev.no.name.blooddesigner.core.MessageImage;
import dev.no.name.blooddesigner.core.MessageRoute;
import dev.no.name.blooddesigner.core.PrivateMessage;
import dev.no.name.blooddesigner.core.PrivateMessages;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.function.Consumer;

/**
 * Пересылка сообщения в другой чат. Пометка «Переслано от …» — чьё оно было: своё, собеседника, BloodDesigner
 * или «Объявления»; переслали уже пересланное — остаётся первый автор, как в Telegram.
 * Картинки другу или админам уходят заново (у каждой своя загрузка), в «Избранное» — ссылкой на кеш
 */
final class Forwarder {
    private final MessageRoute route;
    private final Consumer<String> warn;
    private final Consumer<String> info;

    Forwarder(MessageRoute route, Consumer<String> warn, Consumer<String> info) {
        this.route = route;
        this.warn = warn;
        this.info = info;
    }

    // source — чат, откуда пересылаем; sourceName — как он называется для пометки
    void forward(PrivateMessage message, String sourceName, String target, String targetName) {
        String origin = origin(message, sourceName);
        List<MessageImage> images = message.images();
        if (PrivateMessages.NOTES.equals(target)) {
            if (images.isEmpty()) {
                route.send(target, message.text(), origin, warn);
            } else {
                route.keepNote(message.text(), images, origin);
            }
            info.accept("Переслано в «" + targetName + "»");
            return;
        }
        if (images.isEmpty()) {
            route.send(target, message.text(), origin, warn).ifPresentOrElse(warn, () -> info.accept("Переслано в «" + targetName + "»"));
            return;
        }
        if (images.size() == 1 && images.get(0).voice()) {
            forwardVoice(images.get(0), target, targetName);
            return;
        }
        if (!route.supportsMedia(target)) {
            warn.accept("Картинки можно пересылать только друзьям по сети, в BloodDesigner и в «Избранное»");
            return;
        }
        // у друга и у админов картинка — отдельное сообщение: текст подписью к первой, метки [img] убираем
        String caption = MessageContent.plain(message.text());
        for (int i = 0; i < images.size(); i++) {
            String text = i == 0 ? caption : "";
            route.imageFile(images.get(i)).whenComplete((file, error) -> Minecraft.getInstance().execute(() -> {
                if (error != null) {
                    warn.accept("Картинка ещё не загрузилась — открой её и перешли снова");
                } else {
                    route.sendImage(target, file, text, origin, warn).ifPresent(warn);
                }
            }));
        }
        info.accept("Пересылаю в «" + targetName + "»…");
    }

    // голосовое пересылается файлом записи: тот же путь, что и своё новое голосовое
    private void forwardVoice(MessageImage voice, String target, String targetName) {
        route.imageFile(voice).whenComplete((file, error) -> Minecraft.getInstance().execute(() -> {
            if (error != null) {
                warn.accept("Голосовое ещё не загрузилось — включи его и перешли снова");
                return;
            }
            try {
                byte[] data = java.nio.file.Files.readAllBytes(file);
                route.sendVoice(target, data, voice.voiceCentiseconds(), null, warn)
                        .ifPresentOrElse(warn, () -> info.accept("Пересылаю в «" + targetName + "»…"));
            } catch (java.io.IOException e) {
                warn.accept("Голосовое не прочиталось с диска");
            }
        }));
    }

    private static String origin(PrivateMessage message, String sourceName) {
        if (message.forwardedFrom() != null) {
            return message.forwardedFrom();
        }
        if (message.fromMe()) {
            return Minecraft.getInstance().getUser().getName();
        }
        return sourceName;
    }
}
