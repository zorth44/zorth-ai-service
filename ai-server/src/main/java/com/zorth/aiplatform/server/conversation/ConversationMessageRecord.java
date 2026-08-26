package com.zorth.aiplatform.server.conversation;

import java.time.Instant;

public record ConversationMessageRecord(
        String id,
        String conversationId,
        String role,
        String content,
        String toolsJson,
        Instant createdAt) {
}
