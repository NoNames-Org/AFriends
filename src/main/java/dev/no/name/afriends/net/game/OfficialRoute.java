package dev.no.name.afriends.net.game;

import dev.no.name.afriends.core.MessageRoute;
import dev.no.name.afriends.core.PrivateMessage;
import dev.no.name.afriends.core.PrivateMessages;
import dev.no.name.afriends.core.SocialData;
import dev.no.name.afriends.net.media.PreparedImage;
import dev.no.name.afriends.net.model.Connection;
import dev.no.name.afriends.net.rpc.Failures;
import dev.no.name.afriends.net.service.LinkService;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

// Чат «AFriends»: сообщения и картинки админам мода, на портал. Не шифруются — их читают админы.
// Портал не знает пометки «переслано», поэтому пересланное уходит со строкой «Переслано от …» в тексте
final class OfficialRoute {
    private final SocialData social;
    private final LinkService link;

    OfficialRoute(SocialData social, LinkService link) {
        this.social = social;
        this.link = link;
    }

    Optional<String> send(String text, String forwardedFrom, Consumer<String> failed) {
        if (link.snapshot().connection() != Connection.ONLINE) {
            return Optional.of(offline());
        }
        link.official().send(withPrefix(text, forwardedFrom)).thenAccept(code -> Minecraft.getInstance().execute(() -> {
            if ("ok".equals(code)) {
                social.messages().add(PrivateMessages.OFFICIAL, new PrivateMessage(true, text, System.currentTimeMillis(),
                        null, List.of(), null, null, forwardedFrom));
            } else {
                failed.accept(problem(code));
            }
        }));
        return Optional.empty();
    }

    Optional<String> sendImage(Path file, String caption, String forwardedFrom, Consumer<String> failed) {
        if (link.snapshot().connection() != Connection.ONLINE) {
            return Optional.of(offline());
        }
        CompletableFuture.supplyAsync(() -> PreparedImage.read(file))
                .thenCompose(prepared -> link.official().sendImage(prepared, withPrefix(caption, forwardedFrom)))
                .whenComplete((image, error) -> Minecraft.getInstance().execute(() -> {
                    if (error == null) {
                        social.messages().add(PrivateMessages.OFFICIAL, new PrivateMessage(true, caption, System.currentTimeMillis(),
                                null, List.of(image), null, null, forwardedFrom));
                    } else {
                        failed.accept(problem(Failures.codeOf(error)));
                    }
                }));
        return Optional.empty();
    }

    private static String withPrefix(String text, String forwardedFrom) {
        return forwardedFrom == null ? text : MessageRoute.forwardedPrefix(forwardedFrom) + text;
    }

    private static String offline() {
        return "Нет связи с сетью AFriends — включи сеть в настройках";
    }

    static String problem(String code) {
        return switch (code) {
            case "rate_limited" -> "Слишком часто — подожди минуту";
            case "too_many" -> "Админы ещё не прочитали прошлые сообщения";
            case "bad_text" -> "Слишком длинное сообщение";
            case "bad_images", "upload_failed" -> "Картинка не загрузилась на сервер";
            case "image_too_big" -> "Картинка больше 5 МБ";
            case "image_format" -> "Можно только PNG, JPG или GIF";
            default -> LinkText.sendProblem(code);
        };
    }
}
