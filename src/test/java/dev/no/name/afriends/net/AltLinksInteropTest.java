package dev.no.name.afriends.net;

import dev.no.name.afriends.net.announcements.AnnouncementFeed;
import dev.no.name.afriends.net.chat.InboxListener;
import dev.no.name.afriends.net.model.Connection;
import dev.no.name.afriends.net.model.LinkFriend;
import dev.no.name.afriends.net.service.LinkService;
import dev.no.name.afriends.net.service.PlayerAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// Твинки на настоящем сервере: основной аккаунт входит в сеть, потом игрок «переключается» на второй,
// и второй присылает подпись основного. Итог видно на портале (у основного — «Твинки: …»);
// имена аккаунтов печатаются в вывод теста. Запускается только с LINK_TEST_URL и LINK_TEST_KEY
class AltLinksInteropTest {
    private static final long WAIT_MS = 30_000;

    private static final InboxListener QUIET = new InboxListener() {
        @Override
        public void text(LinkFriend from, String text, long time) {
        }

        @Override
        public void unreadable(LinkFriend from, long time) {
        }
    };

    private static LinkService player(Path folder, String name) {
        return new LinkService(folder, folder.resolveSibling("media"), () -> new PlayerAccount(name,
                challenge -> {
                    throw new IllegalStateException("в тесте без Mojang");
                }), QUIET, new AnnouncementFeed());
    }

    private static void waitFor(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_MS;
        while (!condition.getAsBoolean()) {
            assertTrue(System.currentTimeMillis() < deadline, "не дождались: " + what);
            Thread.sleep(100);
        }
    }

    @Test
    void secondAccountLinksTheFirst(@TempDir Path folder) throws Exception {
        String url = System.getenv("LINK_TEST_URL");
        String key = System.getenv("LINK_TEST_KEY");
        assumeTrue(url != null && key != null, "нет LINK_TEST_URL/LINK_TEST_KEY");
        System.setProperty("afriends.link.url", url);
        System.setProperty("afriends.link.key", key);
        String suffix = Long.toString(System.nanoTime() % 1_000_000L, 36);
        Path mainKeys = folder.resolve("accounts").resolve("main");
        Path twinkKeys = folder.resolve("accounts").resolve("twink");

        LinkService main = player(mainKeys, "altMain" + suffix);
        main.start();
        waitFor("вход основного", () -> main.snapshot().connection() == Connection.ONLINE && main.snapshot().synced());
        main.close();

        LinkService twink = player(twinkKeys, "altTwink" + suffix);
        twink.switchAccount(twinkKeys, List.of(mainKeys));
        twink.start();
        waitFor("вход твинка", () -> twink.snapshot().connection() == Connection.ONLINE && twink.snapshot().synced());
        twink.close();

        // вернулся на основной ник, но переключатель аккаунтов выдал ему другой UUID — у ника новые ключи.
        // Старые ключи этого ника лежат на том же компьютере, поэтому ник переходит к новым, а не «занят»
        Path mainAgainKeys = folder.resolve("accounts").resolve("main-new-uuid");
        LinkService mainAgain = player(mainAgainKeys, "altMain" + suffix);
        mainAgain.switchAccount(mainAgainKeys, List.of(mainKeys, twinkKeys));
        mainAgain.start();
        waitFor("вход основного с новыми ключами", () -> mainAgain.snapshot().connection() == Connection.ONLINE
                && mainAgain.snapshot().synced());
        assertEquals("altMain" + suffix, mainAgain.snapshot().myName());
        mainAgain.close();

        // а чужой ник так не забрать: у этого игрока нет ключей владельца
        Path strangerKeys = folder.resolve("stranger");
        LinkService stranger = player(strangerKeys, "altMain" + suffix);
        stranger.start();
        waitFor("отказ чужому", () -> stranger.snapshot().connection() == Connection.NAME_TAKEN);
        stranger.close();
        System.out.println("ALT_TEST main=altMain" + suffix + " twink=altTwink" + suffix);
    }
}
