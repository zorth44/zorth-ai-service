package com.zorth.aiplatform.agent.conversation;

import java.util.List;

public record AgentTurnAppend(
        String userId,
        String conversationId,
        String userContent,
        String assistantContent,
        List<AgentToolSummary> tools,
        String datasourceId,
        String database) {

    public AgentTurnAppend {
        tools = tools == null ? List.of() : List.copyOf(tools);
    }
}
