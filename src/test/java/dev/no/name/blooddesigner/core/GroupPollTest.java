package dev.no.name.blooddesigner.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupPollTest {
    private static final String CHAT = PrivateMessages.groupKey("8899aabbccddeeff");
    private static final String ID = "0011223344556677";

    @Test
    @DisplayName("варианты чистятся; пустые, повторы и меньше двух — не опрос")
    void cleanOptions() {
        assertEquals(List.of("FFA", "СМП"), GroupPoll.cleanOptions(List.of("  FFA ", "С§МП")));
        assertNull(GroupPoll.cleanOptions(List.of("один")));
        assertNull(GroupPoll.cleanOptions(List.of("Да", "да")));
        assertNull(GroupPoll.cleanOptions(List.of("Да", " ")));
        assertNull(GroupPoll.cleanOptions(List.of("a", "x".repeat(GroupPoll.MAX_OPTION + 1))));
    }

    @Test
    @DisplayName("голоса: свой и чужие считаются вместе, переголосовать и отозвать можно")
    void counting() {
        GroupPoll poll = GroupPoll.fresh(List.of("A", "B", "C"))
                .withVote("Steve", 0).withVote("Alex", 1).withMine(1);
        assertTrue(poll.voted());
        assertEquals(3, poll.total());
        assertEquals(1, poll.votes(0));
        assertEquals(2, poll.votes(1));
        assertEquals(67, poll.share(1));
        poll = poll.withVote("steve", 2).withVote("ALEX", -1).withMine(-1);
        assertFalse(poll.voted());
        assertEquals(1, poll.total());
        assertEquals(1, poll.votes(2));
        assertEquals(poll, poll.withVote("Steve", 7));
    }

    @Test
    @DisplayName("опрос и голоса сохраняются в переписке")
    void savedWithMessage() {
        GroupPoll poll = GroupPoll.fresh(List.of("A", "B")).withVote("Steve", 1).withMine(0);
        PrivateMessage message = new PrivateMessage(false, "Вопрос?", 5, ID, List.of(), null, null, null, null, "Steve", poll);
        assertEquals(message, MessageJson.read(MessageJson.write(message)));
        PrivateMessage plain = new PrivateMessage(false, "просто текст", 6);
        assertNull(MessageJson.read(MessageJson.write(plain)).poll());
    }

    @Test
    @DisplayName("голос по сети находит опрос по id; не опрос и чужой id — нет")
    void voteInChat() {
        PrivateMessages messages = new PrivateMessages();
        messages.add(CHAT, new PrivateMessage(true, "Вопрос?", 1, ID, List.of(), null, null, null, null, null,
                GroupPoll.fresh(List.of("A", "B"))));
        messages.add(CHAT, new PrivateMessage(false, "текст", 2, "aaaaaaaaaaaaaaaa", List.of(), null, null, null, null, "Alex"));
        assertTrue(messages.vote(CHAT, ID, "Alex", 1));
        assertTrue(messages.vote(CHAT, ID, null, 0));
        assertFalse(messages.vote(CHAT, "aaaaaaaaaaaaaaaa", "Alex", 0));
        assertFalse(messages.vote(CHAT, "ffffffffffffffff", "Alex", 0));
        GroupPoll poll = messages.find(CHAT, ID).poll();
        assertEquals(1, poll.votes(0));
        assertEquals(1, poll.votes(1));
        assertEquals("Опрос: Вопрос?", MessageContent.preview(messages.find(CHAT, ID)));
    }
}
