package com.zorth.aiplatform.server.conversation;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConversationDetail(
        String id,
        String title,
        String datasourceId,
        String database,
        Instant updatedAt,
        List<ConversationMessageResponse> messages) {
}
