package com.zorth.aiplatform.agent.conversation;

import com.zorth.aiplatform.core.exception.AiClientException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryAgentConversationMemory implements AgentConversationMemory {

    private final Map<String, OwnedConversation> conversations = new ConcurrentHashMap<>();

    @Override
    public void ensureOwned(String userId, String conversationId) {
        conversations.compute(conversationId, (id, existing) -> {
            if (existing == null) {
                return new OwnedConversation(userId);
            }
            if (!userId.equals(existing.userId)) {
                throw AiClientException.conversationNotFound();
            }
            return existing;
        });
    }

    @Override
    public List<AgentStoredMessage> loadRecentMessages(
            String userId, String conversationId, int maxMessages) {
        OwnedConversation conversation = conversations.get(conversationId);
        if (conversation == null || !userId.equals(conversation.userId)) {
            return List.of();
        }
        int from = Math.max(0, conversation.messages.size() - maxMessages);
        return List.copyOf(conversation.messages.subList(from, conversation.messages.size()));
    }

    @Override
    public void appendTurn(AgentTurnAppend turn) {
        OwnedConversation conversation = conversations.get(turn.conversationId());
        if (conversation == null || !turn.userId().equals(conversation.userId)) {
            throw AiClientException.conversationNotFound();
        }
        conversation.messages.add(new AgentStoredMessage(AgentStoredMessage.ROLE_USER, turn.userContent()));
        conversation.messages.add(new AgentStoredMessage(
                AgentStoredMessage.ROLE_ASSISTANT, turn.assistantContent()));
        conversation.turns.add(turn);
        if (conversation.title == null) {
            conversation.title = turn.userContent();
        }
        if (turn.datasourceId() != null) {
            conversation.datasourceId = turn.datasourceId();
        }
        if (turn.database() != null) {
            conversation.database = turn.database();
        }
    }

    public boolean exists(String conversationId) {
        return conversations.containsKey(conversationId);
    }

    public String owner(String conversationId) {
        OwnedConversation conversation = conversations.get(conversationId);
        return conversation == null ? null : conversation.userId;
    }

    public List<AgentStoredMessage> allMessages(String conversationId) {
        OwnedConversation conversation = conversations.get(conversationId);
        return conversation == null ? List.of() : List.copyOf(conversation.messages);
    }

    public String title(String conversationId) {
        OwnedConversation conversation = conversations.get(conversationId);
        return conversation == null ? null : conversation.title;
    }

    public String datasourceId(String conversationId) {
        OwnedConversation conversation = conversations.get(conversationId);
        return conversation == null ? null : conversation.datasourceId;
    }

    public String database(String conversationId) {
        OwnedConversation conversation = conversations.get(conversationId);
        return conversation == null ? null : conversation.database;
    }

    public List<String> conversationIdsFor(String userId) {
        return conversations.entrySet().stream()
                .filter(entry -> userId.equals(entry.getValue().userId))
                .map(Map.Entry::getKey)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    public void delete(String conversationId) {
        conversations.remove(conversationId);
    }

    private static final class OwnedConversation {
        private final String userId;
        private final List<AgentStoredMessage> messages = new ArrayList<>();
        private final List<AgentTurnAppend> turns = new ArrayList<>();
        private String title;
        private String datasourceId;
        private String database;

        private OwnedConversation(String userId) {
            this.userId = userId;
        }
    }
}
