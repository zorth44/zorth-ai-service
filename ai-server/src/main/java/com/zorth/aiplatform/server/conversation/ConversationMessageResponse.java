package com.zorth.aiplatform.server.conversation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zorth.aiplatform.agent.conversation.AgentToolSummary;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConversationMessageResponse(
        String id,
        String role,
        String content,
        List<AgentToolSummary> tools,
        Instant createdAt) {
}
