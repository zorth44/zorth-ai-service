package com.zorth.aiplatform.core.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank(message = "message must not be blank")
        @Size(max = 10_000, message = "message must not exceed 10000 characters")
        String message,
        @Size(max = 128, message = "conversationId must not exceed 128 characters")
        String conversationId) {

    public ChatRequest(String message) {
        this(message, null);
    }

    public ChatRequest {
        conversationId = blankToNull(conversationId);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
