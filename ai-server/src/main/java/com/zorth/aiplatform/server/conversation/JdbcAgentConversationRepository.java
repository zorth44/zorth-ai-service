package com.zorth.aiplatform.server.conversation;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public final class JdbcAgentConversationRepository {

    static final int LIST_LIMIT = 50;

    private static final RowMapper<ConversationRecord> CONVERSATION_MAPPER = (rs, rowNum) ->
            new ConversationRecord(
                    rs.getString("id"),
                    rs.getString("user_id"),
                    rs.getString("title"),
                    rs.getString("datasource_id"),
                    rs.getString("database_name"),
                    toInstant(rs.getTimestamp("created_at")),
                    toInstant(rs.getTimestamp("updated_at")));

    private static final RowMapper<ConversationMessageRecord> MESSAGE_MAPPER = (rs, rowNum) ->
            new ConversationMessageRecord(
                    rs.getString("id"),
                    rs.getString("conversation_id"),
                    rs.getString("role"),
                    rs.getString("content"),
                    rs.getString("tools_json"),
                    toInstant(rs.getTimestamp("created_at")));

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private Instant lastTimestamp = Instant.EPOCH;

    public JdbcAgentConversationRepository(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public Optional<ConversationRecord> findById(String id) {
        try {
            return Optional.of(jdbc.queryForObject(
                    """
                    SELECT id, user_id, title, datasource_id, database_name, created_at, updated_at
                    FROM agent_conversation
                    WHERE id = ?
                    """,
                    CONVERSATION_MAPPER,
                    id));
        }
        catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public Optional<ConversationRecord> findOwned(String userId, String id) {
        try {
            return Optional.of(jdbc.queryForObject(
                    """
                    SELECT id, user_id, title, datasource_id, database_name, created_at, updated_at
                    FROM agent_conversation
                    WHERE id = ? AND user_id = ?
                    """,
                    CONVERSATION_MAPPER,
                    id,
                    userId));
        }
        catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public List<ConversationRecord> listByUser(String userId, int limit) {
        return jdbc.query(
                """
                SELECT id, user_id, title, datasource_id, database_name, created_at, updated_at
                FROM agent_conversation
                WHERE user_id = ?
                ORDER BY updated_at DESC, id DESC
                LIMIT ?
                """,
                CONVERSATION_MAPPER,
                userId,
                limit);
    }

    public void insert(String id, String userId) {
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO agent_conversation
                    (id, user_id, title, datasource_id, database_name, created_at, updated_at)
                VALUES (?, ?, NULL, NULL, NULL, ?, ?)
                """,
                id,
                userId,
                Timestamp.from(now),
                Timestamp.from(now));
    }

    public boolean deleteOwned(String userId, String id) {
        return jdbc.update(
                "DELETE FROM agent_conversation WHERE id = ? AND user_id = ?",
                id,
                userId) > 0;
    }

    public List<ConversationMessageRecord> listMessages(String conversationId) {
        return jdbc.query(
                """
                SELECT id, conversation_id, role, content, tools_json, created_at
                FROM agent_message
                WHERE conversation_id = ?
                ORDER BY created_at ASC, id ASC
                """,
                MESSAGE_MAPPER,
                conversationId);
    }

    public List<ConversationMessageRecord> loadRecentMessages(
            String userId, String conversationId, int maxMessages) {
        return jdbc.query(
                """
                SELECT id, conversation_id, role, content, tools_json, created_at
                FROM (
                    SELECT m.id, m.conversation_id, m.role, m.content, m.tools_json, m.created_at
                    FROM agent_message m
                    INNER JOIN agent_conversation c ON c.id = m.conversation_id
                    WHERE c.id = ? AND c.user_id = ?
                    ORDER BY m.created_at DESC, m.id DESC
                    LIMIT ?
                ) recent
                ORDER BY created_at ASC, id ASC
                """,
                MESSAGE_MAPPER,
                conversationId,
                userId,
                maxMessages);
    }

    public void insertMessage(
            String conversationId, String role, String content, String toolsJson) {
        jdbc.update(
                """
                INSERT INTO agent_message (id, conversation_id, role, content, tools_json, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID().toString(),
                conversationId,
                role,
                content,
                toolsJson,
                Timestamp.from(nextInstant()));
    }

    public void updateAfterTurn(
            String conversationId,
            String titleIfEmpty,
            String datasourceId,
            String database) {
        Instant now = clock.instant();
        jdbc.update(
                """
                UPDATE agent_conversation
                SET title = CASE
                        WHEN (title IS NULL OR title = '') AND ? IS NOT NULL THEN ?
                        ELSE title
                    END,
                    datasource_id = CASE WHEN ? IS NOT NULL THEN ? ELSE datasource_id END,
                    database_name = CASE WHEN ? IS NOT NULL THEN ? ELSE database_name END,
                    updated_at = ?
                WHERE id = ?
                """,
                titleIfEmpty,
                titleIfEmpty,
                datasourceId,
                datasourceId,
                database,
                database,
                Timestamp.from(now),
                conversationId);
    }

    private Instant nextInstant() {
        Instant now = clock.instant();
        if (!now.isAfter(lastTimestamp)) {
            now = lastTimestamp.plusMillis(1);
        }
        lastTimestamp = now;
        return now;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
