package com.zorth.aiplatform.core.chat;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatStreamEvent(
        String type,
        String content,
        String conversationId,
        String code,
        String message) {

    public static final String TYPE_START = "start";
    public static final String TYPE_DELTA = "delta";
    public static final String TYPE_COMPLETED = "completed";
    public static final String TYPE_ERROR = "error";

    public static ChatStreamEvent start(String conversationId) {
        return new ChatStreamEvent(TYPE_START, null, conversationId, null, null);
    }

    public static ChatStreamEvent delta(String content) {
        return new ChatStreamEvent(TYPE_DELTA, content, null, null, null);
    }

    public static ChatStreamEvent completed(String conversationId) {
        return new ChatStreamEvent(TYPE_COMPLETED, null, conversationId, null, null);
    }

    public static ChatStreamEvent error() {
        return new ChatStreamEvent(
                TYPE_ERROR,
                null,
                null,
                "AI_SERVICE_ERROR",
                "The AI service is temporarily unavailable");
    }
}
