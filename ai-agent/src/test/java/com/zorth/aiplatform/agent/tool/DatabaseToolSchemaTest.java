package com.zorth.aiplatform.agent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zorth.aiplatform.agent.support.ToolContextKeys;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

class DatabaseToolSchemaTest {

    private H2DatabaseToolFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new H2DatabaseToolFixture();
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    @Test
    void generatedSchemasExposeOnlyModelControlledArguments() {
        Map<String, ToolCallback> callbacks = Arrays.stream(MethodToolCallbackProvider.builder()
                        .toolObjects(fixture.tools())
                        .build()
                        .getToolCallbacks())
                .collect(Collectors.toMap(
                        callback -> callback.getToolDefinition().name(), Function.identity()));

        assertEquals(4, callbacks.size());
        assertTrue(callbacks.keySet().containsAll(Arrays.asList(
                "listTables", "getTableSchema", "checkSql", "executeQuery")));

        callbacks.values().forEach(callback -> {
            String description = callback.getToolDefinition().description();
            String schema = callback.getToolDefinition().inputSchema();
            assertTrue(description.contains("Use this"));
            assertFalse(schema.contains(ToolContextKeys.REQUEST_ID));
            assertFalse(schema.contains("userId"));
            assertFalse(schema.contains("conversationId"));
            assertFalse(schema.contains("datasourceId"));
            assertFalse(schema.contains("executionId"));
        });

        assertTrue(callbacks.get("getTableSchema").getToolDefinition().inputSchema()
                .contains("tableNames"));
        assertTrue(callbacks.get("checkSql").getToolDefinition().inputSchema().contains("sql"));
        assertTrue(callbacks.get("executeQuery").getToolDefinition().inputSchema().contains("sql"));
    }
}
