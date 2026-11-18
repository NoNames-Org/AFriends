package dev.no.name.afriends.gui.util;

import dev.no.name.afriends.AFriends;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Открыть ссылку в системном браузере. Через системную команду, а не маппинги Minecraft: способ открыть
 * URL у каждой версии клиента свой, а так один код работает везде. URL здесь всегда свой постоянный адрес
 * (страница проекта), не пользовательский ввод, и аргументы передаются массивом — оболочка их не разбирает
 */
public final class SystemBrowser {
    private static final Logger LOGGER = LoggerFactory.getLogger(AFriends.NAME);

    private SystemBrowser() {
    }

    public static void open(String url) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            ProcessBuilder command;
            if (os.contains("win")) {
                command = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
            } else if (os.contains("mac")) {
                command = new ProcessBuilder("open", url);
            } else {
                command = new ProcessBuilder("xdg-open", url);
            }
            command.start();
        } catch (Exception e) {
            LOGGER.warn("{}: не удалось открыть ссылку {}", AFriends.NAME, url, e);
        }
    }
}
