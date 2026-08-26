package com.zorth.aiplatform.server.conversation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zorth.aiplatform.agent.conversation.AgentToolSummary;
import com.zorth.aiplatform.core.exception.AiClientException;
import java.util.List;

public final class AgentConversationQueryService {

    private static final TypeReference<List<AgentToolSummary>> TOOL_LIST = new TypeReference<>() {};

    private final JdbcAgentConversationRepository repository;
    private final ObjectMapper objectMapper;

    public AgentConversationQueryService(
            JdbcAgentConversationRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public List<ConversationListItem> list(String userId) {
        return repository.listByUser(userId, JdbcAgentConversationRepository.LIST_LIMIT).stream()
                .map(this::toListItem)
                .toList();
    }

    public ConversationDetail get(String userId, String id) {
        ConversationRecord conversation = repository.findOwned(userId, id)
                .orElseThrow(AiClientException::conversationNotFound);
        List<ConversationMessageResponse> messages = repository.listMessages(id).stream()
                .map(this::toMessage)
                .toList();
        return new ConversationDetail(
                conversation.id(),
                conversation.title(),
                conversation.datasourceId(),
                conversation.database(),
                conversation.updatedAt(),
                messages);
    }

    public void delete(String userId, String id) {
        if (!repository.deleteOwned(userId, id)) {
            throw AiClientException.conversationNotFound();
        }
    }

    private ConversationListItem toListItem(ConversationRecord conversation) {
        return new ConversationListItem(
                conversation.id(),
                conversation.title(),
                conversation.datasourceId(),
                conversation.database(),
                conversation.updatedAt());
    }

    private ConversationMessageResponse toMessage(ConversationMessageRecord message) {
        return new ConversationMessageResponse(
                message.id(),
                message.role(),
                message.content(),
                readTools(message.toolsJson()),
                message.createdAt());
    }

    private List<AgentToolSummary> readTools(String toolsJson) {
        if (toolsJson == null || toolsJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(toolsJson, TOOL_LIST);
        }
        catch (JsonProcessingException exception) {
            return null;
        }
    }
}
