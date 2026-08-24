package com.zorth.aiplatform.agent.support;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

class DatabaseToolAuditTest {

    @Test
    void renderIncludesDatabaseAndExecutionIdButNotAuthorization() {
        DatabaseToolAudit audit = new DatabaseToolAudit();
        ToolContext context = new ToolContext(Map.of(
                ToolContextKeys.REQUEST_ID, "req-1",
                ToolContextKeys.CONVERSATION_ID, "conv-1",
                ToolContextKeys.USER_ID, "user-1",
                ToolContextKeys.DATASOURCE_ID, "ds-1",
                ToolContextKeys.DATABASE, "orders",
                ToolContextKeys.AUTHORIZATION, "Bearer secret-token"));

        String line = audit.render(
                context,
                "executeQuery",
                "SELECT id FROM orders",
                "11111111-1111-1111-1111-111111111111",
                "SUCCESS",
                12L,
                2,
                null);

        assertTrue(line.contains("database=orders"));
        assertTrue(line.contains("executionId=11111111-1111-1111-1111-111111111111"));
        assertTrue(line.contains("datasourceId=ds-1"));
        assertFalse(line.contains("secret-token"));
        assertFalse(line.contains("Bearer"));
        assertFalse(line.contains("authorization"));
    }
}
