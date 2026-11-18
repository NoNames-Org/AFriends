package dev.no.name.afriends.net.chat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.no.name.afriends.core.GroupPoll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatPayloadPollTest {
    private static final String ID = "0011223344556677";
    private static final String GROUP = "8899aabbccddeeff";

    @Test
    @DisplayName("опрос в группе доходит с вопросом и вариантами, без запасного текста")
    void poll() {
        ChatPayload.Text sent = new ChatPayload.Text("Куда идём?", ID, null, null, null, GROUP, List.of("FFA", "СМП"));
        ChatPayload.Text text = assertInstanceOf(ChatPayload.Text.class, ChatPayload.decode(sent.encode()));
        assertEquals("Куда идём?", text.text());
        assertEquals(List.of("FFA", "СМП"), text.poll());
        assertEquals(GROUP, text.group());
        assertEquals(ID, text.id());
    }

    @Test
    @DisplayName("версия без опросов видит вопрос, варианты и просьбу обновиться")
    void oldVersionSeesText() {
        byte[] encoded = new ChatPayload.Text("Куда идём?", ID, null, null, null, GROUP, List.of("FFA", "СМП")).encode();
        JsonObject json = JsonParser.parseString(new String(encoded, StandardCharsets.UTF_8)).getAsJsonObject();
        json.remove("p");
        ChatPayload.Text old = assertInstanceOf(ChatPayload.Text.class, ChatPayload.decode(json.toString().getBytes(StandardCharsets.UTF_8)));
        assertTrue(old.text().startsWith("Куда идём?\nОпрос:\n1. FFA\n2. СМП"));
        assertTrue(old.text().contains("обнови AFriends"));
        assertNull(old.poll());
    }

    @Test
    @DisplayName("самый длинный опрос с запасным текстом не длиннее сообщения: старая версия его не выбросит")
    void longestPollFitsOldLimit() {
        List<String> options = new ArrayList<>();
        for (int i = 0; i < GroupPoll.MAX_OPTIONS; i++) {
            options.add((char) ('a' + i) + "x".repeat(GroupPoll.MAX_OPTION - 1));
        }
        String question = "q".repeat(GroupPoll.MAX_QUESTION);
        String shown = question + "\n" + ChatPayload.pollFallback(options);
        assertTrue(shown.length() <= ChatPayload.MAX_TEXT, "длина " + shown.length());
    }

    @Test
    @DisplayName("опрос вне группы, без id или с одним вариантом отбрасывается")
    void badPolls() {
        assertNull(ChatPayload.decode(new ChatPayload.Text("?", ID, null, null, null, null, List.of("a", "b")).encode()));
        assertNull(ChatPayload.decode(new ChatPayload.Text("?", null, null, null, null, GROUP, List.of("a", "b")).encode()));
        String single = "{\"k\":\"text\",\"t\":\"?\",\"id\":\"" + ID + "\",\"g\":\"" + GROUP + "\",\"p\":[\"a\"]}";
        assertNull(ChatPayload.decode(single.getBytes(StandardCharsets.UTF_8)));
        String twice = "{\"k\":\"text\",\"t\":\"?\",\"id\":\"" + ID + "\",\"g\":\"" + GROUP + "\",\"p\":[\"Да\",\"да\"]}";
        assertNull(ChatPayload.decode(twice.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("голос и отзыв голоса доходят; номер вне вариантов и битый id — нет")
    void vote() {
        ChatPayload.Vote vote = assertInstanceOf(ChatPayload.Vote.class, ChatPayload.decode(new ChatPayload.Vote(GROUP, ID, 2).encode()));
        assertEquals(new ChatPayload.Vote(GROUP, ID, 2), vote);
        assertEquals(-1, ((ChatPayload.Vote) ChatPayload.decode(new ChatPayload.Vote(GROUP, ID, -1).encode())).choice());
        String far = "{\"k\":\"vote\",\"g\":\"" + GROUP + "\",\"to\":\"" + ID + "\",\"c\":99}";
        assertNull(ChatPayload.decode(far.getBytes(StandardCharsets.UTF_8)));
        String badId = "{\"k\":\"vote\",\"g\":\"" + GROUP + "\",\"to\":\"t1m\",\"c\":0}";
        assertNull(ChatPayload.decode(badId.getBytes(StandardCharsets.UTF_8)));
    }
}
