package dev.no.name.blooddesigner.net;

import dev.no.name.blooddesigner.core.MessageImage;
import dev.no.name.blooddesigner.net.chat.InboxListener;
import dev.no.name.blooddesigner.net.media.PreparedImage;
import dev.no.name.blooddesigner.net.model.ClientKind;
import dev.no.name.blooddesigner.net.model.Connection;
import dev.no.name.blooddesigner.net.model.LinkFriend;
import dev.no.name.blooddesigner.net.model.LinkRequest;
import dev.no.name.blooddesigner.net.model.Presence;
import dev.no.name.blooddesigner.net.service.LinkService;
import dev.no.name.blooddesigner.net.service.LocalProfile;
import dev.no.name.blooddesigner.net.service.PlayerAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// Служба сети целиком на настоящем сервере: вход, дружба, статусы по ключу отправителя и переписка.
// Запускается только когда заданы LINK_TEST_URL и LINK_TEST_KEY, как и LinkInteropTest
class LinkServiceInteropTest {
    private static final long WAIT_MS = 30_000;

    private static final class Player implements InboxListener {
        final String name;
        final LinkService service;
        final BlockingQueue<String> texts = new LinkedBlockingQueue<>();
        final BlockingQueue<MessageImage> images = new LinkedBlockingQueue<>();
        final BlockingQueue<String> captions = new LinkedBlockingQueue<>();
        final BlockingQueue<String> reactions = new LinkedBlockingQueue<>();

        Player(String name, Path folder, ClientKind client) {
            this.name = name;
            this.service = new LinkService(folder, () -> new PlayerAccount(name, client, challenge -> {
                throw new IllegalStateException("в тесте без Mojang");
            }), this);
        }

        @Override
        public void text(LinkFriend from, String text, long time) {
            texts.add(from.name() + ": " + text);
        }

        @Override
        public void message(LinkFriend from, String text, String id, MessageImage image, long time) {
            if (image != null) {
                images.add(image);
                captions.add(text);
            } else {
                text(from, text, time);
            }
        }

        @Override
        public void reaction(LinkFriend from, String target, String reaction, long time) {
            reactions.add(from.name() + " " + target + " " + reaction);
        }

        @Override
        public void unreadable(LinkFriend from, long time) {
            texts.add(from.name() + ": <не расшифровалось>");
        }

        LinkFriend friend(String other) {
            return service.snapshot().friendNamed(other);
        }
    }

    private static void waitFor(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_MS;
        while (!condition.getAsBoolean()) {
            assertTrue(System.currentTimeMillis() < deadline, "не дождались: " + what);
            Thread.sleep(100);
        }
    }

    private static LocalProfile on(String server) {
        return new LocalProfile(new Presence(Presence.Where.SERVER, server, "тест", System.currentTimeMillis()), 0xFF0000, true);
    }

    @Test
    void friendsSeeEachOtherAndTalk(@TempDir Path folder) throws Exception {
        String url = System.getenv("LINK_TEST_URL");
        String key = System.getenv("LINK_TEST_KEY");
        assumeTrue(url != null && key != null, "нет LINK_TEST_URL/LINK_TEST_KEY");
        System.setProperty("blooddesigner.link.url", url);
        System.setProperty("blooddesigner.link.key", key);
        String suffix = Long.toString(System.nanoTime() % 1_000_000L, 36);
        Player alice = new Player("svcA" + suffix, folder.resolve("a"), ClientKind.SMPHELPER);
        Player bob = new Player("svcB" + suffix, folder.resolve("b"), ClientKind.BLOODDESIGNER);
        try {
            alice.service.start();
            bob.service.start();
            alice.service.updateProfile(on("alice.example.net"));
            bob.service.updateProfile(on("bob.example.net"));
            waitFor("вход", () -> alice.service.snapshot().connection() == Connection.ONLINE
                    && bob.service.snapshot().connection() == Connection.ONLINE);

            assertEquals("ok", alice.service.requestFriend(bob.name).get(5, TimeUnit.SECONDS));
            waitFor("заявка у Боба", () -> !bob.service.snapshot().incoming().isEmpty());
            LinkRequest request = bob.service.snapshot().incoming().get(0);
            assertEquals("ok", bob.service.respond(request.id(), true).get(5, TimeUnit.SECONDS));
            waitFor("дружба", () -> alice.friend(bob.name) != null && bob.friend(alice.name) != null);
            assertEquals(ClientKind.SMPHELPER, bob.friend(alice.name).client());
            assertEquals(ClientKind.BLOODDESIGNER, alice.friend(bob.name).client());

            // статусы доходят только после того, как ключ статуса пришёл сквозным сообщением
            waitFor("статус Алисы у Боба", () -> {
                LinkFriend friend = bob.friend(alice.name);
                return friend != null && friend.presence() != null && "alice.example.net".equals(friend.presence().server());
            });
            waitFor("статус Боба у Алисы", () -> {
                LinkFriend friend = alice.friend(bob.name);
                return friend != null && friend.presence() != null && "bob.example.net".equals(friend.presence().server());
            });

            // смена сервера доходит событием presence
            alice.service.updateProfile(on("other.example.net"));
            waitFor("новый сервер Алисы", () -> "other.example.net".equals(bob.friend(alice.name).presence().server()));

            String bobId = alice.friend(bob.name).id();
            String aliceId = bob.friend(alice.name).id();
            assertEquals("ok", alice.service.sendText(bobId, "привет").get(10, TimeUnit.SECONDS));
            assertEquals(alice.name + ": привет", bob.texts.poll(10, TimeUnit.SECONDS));
            assertEquals("ok", bob.service.sendText(aliceId, "и тебе").get(10, TimeUnit.SECONDS));
            assertEquals(bob.name + ": и тебе", alice.texts.poll(10, TimeUnit.SECONDS));

            // картинка: Алиса шифрует и заливает, Боб получает описание, скачивает и расшифровывает тот же файл
            BufferedImage picture = new BufferedImage(64, 48, BufferedImage.TYPE_INT_ARGB);
            picture.setRGB(10, 10, 0xFFFF0000);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(picture, "png", png);
            PreparedImage prepared = PreparedImage.of(png.toByteArray());
            MessageImage sent = alice.service.sendImage(bobId, prepared, "aaaabbbbccccdddd", "смотри").get(30, TimeUnit.SECONDS);
            MessageImage received = bob.images.poll(10, TimeUnit.SECONDS);
            assertEquals(sent, received);
            assertEquals("смотри", bob.captions.poll(1, TimeUnit.SECONDS));
            Path file = bob.service.media().file(received).get(30, TimeUnit.SECONDS);
            assertArrayEquals(png.toByteArray(), Files.readAllBytes(file));

            // реакция на картинку и её снятие
            assertEquals("ok", bob.service.sendReaction(aliceId, "aaaabbbbccccdddd", "fire").get(10, TimeUnit.SECONDS));
            assertEquals(bob.name + " aaaabbbbccccdddd fire", alice.reactions.poll(10, TimeUnit.SECONDS));
            assertEquals("ok", bob.service.sendReaction(aliceId, "aaaabbbbccccdddd", null).get(10, TimeUnit.SECONDS));
            assertEquals(bob.name + " aaaabbbbccccdddd null", alice.reactions.poll(10, TimeUnit.SECONDS));

            String safetyA = alice.service.safetyNumber(bobId).get(5, TimeUnit.SECONDS);
            String safetyB = bob.service.safetyNumber(aliceId).get(5, TimeUnit.SECONDS);
            assertEquals(safetyA, safetyB, "отпечаток одинаковый у обоих");
        } finally {
            alice.service.close();
            bob.service.close();
        }
    }
}
