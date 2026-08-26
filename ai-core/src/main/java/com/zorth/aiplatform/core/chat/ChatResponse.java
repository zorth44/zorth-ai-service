package com.zorth.aiplatform.core.chat;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatResponse(String content, String conversationId) {

    public ChatResponse(String content) {
        this(content, null);
    }
}
