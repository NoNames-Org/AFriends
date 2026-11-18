package dev.no.name.afriends.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ServerMessagesTest {
    private static final String LINE = "-------------------------------------------------------------------";

    private static String block(String... lines) {
        return LINE + "\n" + String.join("\n", lines) + "\n" + LINE;
    }

    @Test
    @DisplayName("разделители и пустые строки отбрасываются")
    void linesStripDelimiters() {
        assertEquals(List.of("Leader: Steve", "Members: [Steve]"),
                ServerMessages.lines(block("Leader: Steve", "Members: [Steve]")));
        assertEquals(List.of("Amber is currently offline"),
                ServerMessages.lines("------\nAmber is currently offline\n-------�"));
    }

    @Test
    @DisplayName("«ник is currently offline» — ответ на ЛС офлайн-игроку")
    void offlinePlayer() {
        assertEquals("Amber", ServerMessages.offlinePlayer(block("Amber is currently offline")));
        assertEquals("Bob", ServerMessages.offlinePlayer("Bob is currently offline"));
        assertNull(ServerMessages.offlinePlayer("<Bob> Amber is currently offline"));
        assertNull(ServerMessages.offlinePlayer("[PM] Fell → Bob: is currently offline"));
    }
}
