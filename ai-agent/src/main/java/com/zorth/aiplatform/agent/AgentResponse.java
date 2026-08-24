package com.zorth.aiplatform.agent;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentResponse(String content, String conversationId) {

    public AgentResponse(String content) {
        this(content, null);
    }
}
