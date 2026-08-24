package com.zorth.aiplatform.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentRequest(
        @NotBlank(message = "message must not be blank")
        @Size(max = 10_000, message = "message must not exceed 10000 characters")
        String message,
        @Size(max = 128, message = "conversationId must not exceed 128 characters")
        String conversationId,
        @Size(max = 128, message = "datasourceId must not exceed 128 characters")
        String datasourceId,
        @Size(max = 128, message = "userId must not exceed 128 characters")
        String userId,
        @Size(max = 128, message = "database must not exceed 128 characters")
        String database) {

    public AgentRequest(String message) {
        this(message, null, null, null, null);
    }

    public AgentRequest(String message, String conversationId, String datasourceId, String userId) {
        this(message, conversationId, datasourceId, userId, null);
    }

    public AgentRequest {
        conversationId = blankToNull(conversationId);
        datasourceId = blankToNull(datasourceId);
        userId = blankToNull(userId);
        database = blankToNull(database);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
