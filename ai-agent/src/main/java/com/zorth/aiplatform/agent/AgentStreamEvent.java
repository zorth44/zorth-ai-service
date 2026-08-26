package com.zorth.aiplatform.agent;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentStreamEvent(
        String type,
        String content,
        String conversationId,
        String toolName,
        String status,
        String code,
        String message) {

    public static final String TYPE_START = "start";
    public static final String TYPE_DELTA = "delta";
    public static final String TYPE_TOOL = "tool";
    public static final String TYPE_COMPLETED = "completed";
    public static final String TYPE_ERROR = "error";

    public static final String STATUS_STARTED = "STARTED";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILURE = "FAILURE";

    public static AgentStreamEvent start(String conversationId) {
        return new AgentStreamEvent(TYPE_START, null, conversationId, null, null, null, null);
    }

    public static AgentStreamEvent delta(String content) {
        return new AgentStreamEvent(TYPE_DELTA, content, null, null, null, null, null);
    }

    public static AgentStreamEvent tool(String toolName, String status) {
        return new AgentStreamEvent(TYPE_TOOL, null, null, toolName, status, null, null);
    }

    public static AgentStreamEvent completed(String conversationId) {
        return new AgentStreamEvent(TYPE_COMPLETED, null, conversationId, null, null, null, null);
    }

    public static AgentStreamEvent error() {
        return new AgentStreamEvent(
                TYPE_ERROR,
                null,
                null,
                null,
                null,
                "AI_SERVICE_ERROR",
                "The AI service is temporarily unavailable");
    }
}
