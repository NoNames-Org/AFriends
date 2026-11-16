package dev.no.name.blooddesigner.net.game;

import dev.no.name.blooddesigner.core.MessageImage;
import dev.no.name.blooddesigner.core.MessageReply;
import dev.no.name.blooddesigner.core.PrivateMessage;
import dev.no.name.blooddesigner.core.SocialData;
import dev.no.name.blooddesigner.net.media.MediaCache;
import dev.no.name.blooddesigner.net.media.PreparedImage;
import dev.no.name.blooddesigner.net.tunnel.TunnelError;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

// «Избранное»: заметки для себя. Ничего никуда не уходит — картинка кладётся в кеш на диске по отпечатку
final class NotesRoute {
    private final SocialData social;
    private final MediaCache media;

    NotesRoute(SocialData social, MediaCache media) {
        this.social = social;
        this.media = media;
    }

    Optional<String> send(String text, String forwardedFrom, MessageReply reply) {
        social.messages().addNote(new PrivateMessage(true, text, System.currentTimeMillis(), null, List.of(), null, null,
                forwardedFrom, reply));
        return Optional.empty();
    }

    Optional<String> sendImage(Path file, String caption, String forwardedFrom, MessageReply reply, Consumer<String> failed) {
        CompletableFuture.supplyAsync(() -> media.keepLocal(PreparedImage.read(file)))
                .whenComplete((image, error) -> Minecraft.getInstance().execute(() -> {
                    if (error == null) {
                        addNote(caption, List.of(image), forwardedFrom, reply);
                    } else {
                        failed.accept(OfficialRoute.problem(TunnelError.codeOf(error)));
                    }
                }));
        return Optional.empty();
    }

    // пересланная картинка уже лежит в кеше (её только что показывали) — заметка ссылается на неё же
    void keep(String text, List<MessageImage> images, String forwardedFrom) {
        addNote(text, images, forwardedFrom, null);
    }

    private void addNote(String text, List<MessageImage> images, String forwardedFrom, MessageReply reply) {
        social.messages().addNote(new PrivateMessage(true, text, System.currentTimeMillis(), null, images, null, null,
                forwardedFrom, reply));
    }
}
