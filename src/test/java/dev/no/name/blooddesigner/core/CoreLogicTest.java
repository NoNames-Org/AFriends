package dev.no.name.blooddesigner.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreLogicTest {
    @Test
    @DisplayName("ЛС от меня и мне разбираются, чужие — нет")
    void privateMessageParse() {
        PrivateMessages.Parsed out = PrivateMessages.parse("[PM] Fell → Bob: привет", "Fell", 5);
        assertEquals("Bob", out.partner());
        assertTrue(out.message().fromMe());
        assertEquals("привет", out.message().text());

        PrivateMessages.Parsed in = PrivateMessages.parse("[ЛС] Bob -> fell: хай", "Fell", 5);
        assertEquals("Bob", in.partner());
        assertFalse(in.message().fromMe());

        assertNull(PrivateMessages.parse("[PM] Ann → Bob: не мне", "Fell", 5));
        assertNull(PrivateMessages.parse("<Bob> обычный чат", "Fell", 5));
        assertNull(PrivateMessages.parse("[PM] Fell → Bob: x", null, 5));
    }

    @Test
    @DisplayName("ник автора в общем чате")
    void chatSender() {
        assertEquals("Bob", ChatFilter.chatSender("<Bob> привет"));
        assertEquals("Bob", ChatFilter.chatSender("68 ♥ Bob: привет"));
        assertNull(ChatFilter.chatSender("Сервер перезапустится через 5 минут"));
    }

    @Test
    @DisplayName("адрес сервера без порта по умолчанию и регистра")
    void serverScopeNormalize() {
        assertEquals("bloodstone.boo", ServerScope.normalize("Bloodstone.boo:25565"));
        assertEquals("bloodstone.boo:25566", ServerScope.normalize(" bloodstone.boo:25566 "));
    }
}
