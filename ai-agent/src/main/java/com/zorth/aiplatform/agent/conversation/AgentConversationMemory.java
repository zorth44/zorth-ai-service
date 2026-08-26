package com.zorth.aiplatform.agent.conversation;

import java.util.List;

public interface AgentConversationMemory {

    void ensureOwned(String userId, String conversationId);

    List<AgentStoredMessage> loadRecentMessages(String userId, String conversationId, int maxMessages);

    void appendTurn(AgentTurnAppend turn);
}
