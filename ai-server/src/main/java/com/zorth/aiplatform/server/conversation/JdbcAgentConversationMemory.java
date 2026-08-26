package com.zorth.aiplatform.server.conversation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zorth.aiplatform.agent.conversation.AgentConversationMemory;
import com.zorth.aiplatform.agent.conversation.AgentStoredMessage;
import com.zorth.aiplatform.agent.conversation.AgentToolSummary;
import com.zorth.aiplatform.agent.conversation.AgentTurnAppend;
import com.zorth.aiplatform.core.exception.AiClientException;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

public class JdbcAgentConversationMemory implements AgentConversationMemory {

    private static final int TITLE_MAX_LENGTH = 80;

    private final JdbcAgentConversationRepository repository;
    private final ObjectMapper objectMapper;

    public JdbcAgentConversationMemory(
            JdbcAgentConversationRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void ensureOwned(String userId, String conversationId) {
        repository.findById(conversationId).ifPresentOrElse(existing -> {
            if (!userId.equals(existing.userId())) {
                throw AiClientException.conversationNotFound();
            }
        }, () -> repository.insert(conversationId, userId));
    }

    @Override
    public List<AgentStoredMessage> loadRecentMessages(
            String userId, String conversationId, int maxMessages) {
        return repository.loadRecentMessages(userId, conversationId, maxMessages).stream()
                .map(row -> new AgentStoredMessage(row.role(), row.content()))
                .toList();
    }

    @Override
    @Transactional
    public void appendTurn(AgentTurnAppend turn) {
        repository.insertMessage(
                turn.conversationId(),
                AgentStoredMessage.ROLE_USER,
                turn.userContent(),
                null);
        repository.insertMessage(
                turn.conversationId(),
                AgentStoredMessage.ROLE_ASSISTANT,
                turn.assistantContent(),
                writeTools(turn.tools()));
        repository.updateAfterTurn(
                turn.conversationId(),
                clipTitle(turn.userContent()),
                turn.datasourceId(),
                turn.database());
    }

    static String clipTitle(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= TITLE_MAX_LENGTH ? trimmed : trimmed.substring(0, TITLE_MAX_LENGTH);
    }

    private String writeTools(List<AgentToolSummary> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(tools);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize tool summaries", exception);
        }
    }
}
