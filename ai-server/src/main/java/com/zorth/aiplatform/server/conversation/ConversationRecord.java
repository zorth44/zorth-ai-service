package com.zorth.aiplatform.server.conversation;

import java.time.Instant;

public record ConversationRecord(
        String id,
        String userId,
        String title,
        String datasourceId,
        String database,
        Instant createdAt,
        Instant updatedAt) {
}
