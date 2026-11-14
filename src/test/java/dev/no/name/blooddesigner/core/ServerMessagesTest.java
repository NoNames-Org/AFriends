package dev.no.name.blooddesigner.core;

import dev.no.name.blooddesigner.core.party.PartyEvent;
import dev.no.name.blooddesigner.core.party.PartyMessages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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

    @Test
    @DisplayName("состав пати из /party info")
    void partyInfo() {
        PartyEvent event = PartyMessages.parse(block("Leader: Steve", "Members: [Steve, Zoey]"));
        PartyEvent.Info info = assertInstanceOf(PartyEvent.Info.class, event);
        assertEquals("Steve", info.leader());
        assertEquals(List.of("Steve", "Zoey"), info.members());
    }

    @Test
    @DisplayName("приглашения: своё отправленное и входящее")
    void invites() {
        PartyEvent.Invited sent = assertInstanceOf(PartyEvent.Invited.class,
                PartyMessages.parse(block("Zoey has been invited to the party!")));
        assertEquals("Zoey", sent.name());
        PartyEvent.IncomingInvite incoming = assertInstanceOf(PartyEvent.IncomingInvite.class,
                PartyMessages.parse(block("Sam_Lexi invited you to their party!", "Click [HERE] to accept")));
        assertEquals("Sam_Lexi", incoming.from());
    }

    @Test
    @DisplayName("выход, роспуск, «не в пати», «нельзя себя»")
    void simpleEvents() {
        assertInstanceOf(PartyEvent.Left.class, PartyMessages.parse(block("You left from the party!")));
        assertInstanceOf(PartyEvent.Disbanded.class, PartyMessages.parse(block("The party was disbanded!")));
        assertInstanceOf(PartyEvent.NotInParty.class, PartyMessages.parse(block("You are not at a party!")));
        assertInstanceOf(PartyEvent.InviteSelf.class, PartyMessages.parse(block("You can't invite yourself!")));
    }

    @Test
    @DisplayName("незнакомое сообщение про пати — Other, обычный чат — null")
    void otherAndUnrelated() {
        PartyEvent.Other other = assertInstanceOf(PartyEvent.Other.class,
                PartyMessages.parse(block("Zoey joined the party!")));
        assertEquals("Zoey joined the party!", other.text());
        assertNull(PartyMessages.parse("<Bob> го в пати"));
        assertNull(PartyMessages.parse("Bob: кто в party?"));
        assertNull(PartyMessages.parse(block("Want a cool hat?", "Buy at donate.bloodstone.gg")));
    }

    @Test
    @DisplayName("команда принятия берётся из клика по [HERE] вместе с UUID")
    void acceptCommandFromClick() {
        String uuid = "4f1c2a3b-1111-2222-3333-444455556666";
        assertEquals("party accept " + uuid,
                PartyMessages.acceptCommand(List.of("/help", "/party accept " + uuid)));
        assertEquals("party accept " + uuid, PartyMessages.acceptCommand(List.of("party accept " + uuid)));
        assertNull(PartyMessages.acceptCommand(List.of("/spawn")));
        assertNull(PartyMessages.acceptCommand(List.of()));
    }
}
