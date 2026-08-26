package com.zorth.aiplatform.server.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zorth.aiplatform.agent.conversation.AgentStoredMessage;
import com.zorth.aiplatform.agent.conversation.AgentToolSummary;
import com.zorth.aiplatform.agent.conversation.AgentTurnAppend;
import com.zorth.aiplatform.core.exception.AiClientException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcAgentConversationRepositoryTest {

    private JdbcAgentConversationRepository repository;
    private JdbcAgentConversationMemory memory;
    private AgentConversationQueryService queries;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:conv-" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        clock = new MutableClock(Instant.parse("2026-08-26T08:00:00Z"));
        repository = new JdbcAgentConversationRepository(new JdbcTemplate(dataSource), clock);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        memory = new JdbcAgentConversationMemory(repository, objectMapper);
        queries = new AgentConversationQueryService(repository, objectMapper);
    }

    @Test
    void missingOrOtherUserIdIsNotFoundAndNeverLeaksTheRow() {
        memory.ensureOwned("user-a", "conv-a");
        memory.appendTurn(new AgentTurnAppend(
                "user-a", "conv-a", "列出订单", "订单如下", List.of(), "ds-1", "orders"));

        assertTrue(repository.findOwned("user-a", "conv-a").isPresent());
        assertTrue(repository.findOwned("user-b", "conv-a").isEmpty());
        assertTrue(repository.findOwned("user-a", "missing").isEmpty());
        assertEquals(List.of("conv-a"), repository.listByUser("user-a", 50).stream()
                .map(ConversationRecord::id)
                .toList());
        assertTrue(repository.listByUser("user-b", 50).isEmpty());
        assertThrows(AiClientException.class, () -> memory.ensureOwned("user-b", "conv-a"));
    }

    @Test
    void titleIsFirstUserTextClippedToEightyAndDatasourceUpdatesOnSuccess() {
        memory.ensureOwned("user-a", "conv-a");
        memory.appendTurn(new AgentTurnAppend(
                "user-a",
                "conv-a",
                "a".repeat(90),
                "ok",
                List.of(new AgentToolSummary("listTables", "SUCCESS")),
                "ds-1",
                "orders"));
        memory.appendTurn(new AgentTurnAppend(
                "user-a", "conv-a", "follow-up", "still ok", List.of(), "ds-2", "sales"));

        ConversationRecord conversation = repository.findOwned("user-a", "conv-a").orElseThrow();
        assertEquals("a".repeat(80), conversation.title());
        assertEquals("ds-2", conversation.datasourceId());
        assertEquals("sales", conversation.database());
        assertEquals(4, repository.listMessages("conv-a").size());
        assertEquals("a".repeat(90), repository.listMessages("conv-a").get(0).content());
        assertTrue(repository.listMessages("conv-a").get(1).toolsJson().contains("listTables"));
    }

    @Test
    void deleteOwnedRemovesMessagesAndLeavesOtherUsersIntact() {
        memory.ensureOwned("user-a", "conv-a");
        memory.appendTurn(new AgentTurnAppend(
                "user-a", "conv-a", "hello", "hi", List.of(), null, null));
        memory.ensureOwned("user-b", "conv-b");
        memory.appendTurn(new AgentTurnAppend(
                "user-b", "conv-b", "other", "reply", List.of(), null, null));

        assertTrue(repository.deleteOwned("user-a", "conv-a"));
        assertFalse(repository.deleteOwned("user-b", "conv-a"));
        assertTrue(repository.findById("conv-a").isEmpty());
        assertTrue(repository.listMessages("conv-a").isEmpty());
        assertTrue(repository.findOwned("user-b", "conv-b").isPresent());
        assertEquals(
                List.of(new AgentStoredMessage("user", "other"),
                        new AgentStoredMessage("assistant", "reply")),
                memory.loadRecentMessages("user-b", "conv-b", 20));
    }

    @Test
    void listIsCappedAndOrderedByUpdatedAt() {
        memory.ensureOwned("user-a", "older");
        memory.appendTurn(new AgentTurnAppend(
                "user-a", "older", "first", "a", List.of(), null, null));
        clock.advanceSeconds(1);
        memory.ensureOwned("user-a", "newer");
        memory.appendTurn(new AgentTurnAppend(
                "user-a", "newer", "second", "b", List.of(), null, null));

        List<ConversationListItem> items = queries.list("user-a");
        assertEquals(List.of("newer", "older"), items.stream().map(ConversationListItem::id).toList());
        assertEquals("second", items.get(0).title());
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
