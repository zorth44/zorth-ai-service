package com.zorth.aiplatform.agent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zorth.aiplatform.agent.model.SystemInfo;
import com.zorth.aiplatform.agent.support.ToolContextKeys;
import com.zorth.aiplatform.agent.support.ToolExecutionSupport;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

class FoundationToolSchemaTest {

    @Test
    void generatedSchemasExposeOnlyTypedModelControlledArguments() {
        ToolExecutionSupport support = new ToolExecutionSupport();
        DateTools dateTools = new DateTools(
                Clock.fixed(Instant.parse("2026-08-21T12:00:00Z"), ZoneOffset.UTC), support);
        CalculatorTools calculatorTools = new CalculatorTools(support);
        SystemTools systemTools = new SystemTools(
                new SystemInfo("ai-platform", "test", "test-version"), support);

        Map<String, ToolCallback> callbacks = Arrays.stream(MethodToolCallbackProvider.builder()
                        .toolObjects(dateTools, calculatorTools, systemTools)
                        .build()
                        .getToolCallbacks())
                .collect(Collectors.toMap(
                        callback -> callback.getToolDefinition().name(), Function.identity()));

        assertEquals(4, callbacks.size());
        assertTrue(callbacks.keySet().containsAll(Arrays.asList(
                "getCurrentDate", "calculateDaysBetween", "calculate", "getSystemInfo")));

        callbacks.values().forEach(callback -> {
            String description = callback.getToolDefinition().description();
            String schema = callback.getToolDefinition().inputSchema();
            assertTrue(description.contains("Use this"));
            assertFalse(schema.contains(ToolContextKeys.REQUEST_ID));
            assertFalse(schema.contains("userId"));
            assertFalse(schema.contains("tenantId"));
            assertFalse(schema.contains("datasourceId"));
            assertFalse(schema.contains("executionId"));
        });

        String calculatorSchema = callbacks.get("calculate").getToolDefinition().inputSchema();
        assertTrue(calculatorSchema.contains("left"));
        assertTrue(calculatorSchema.contains("right"));
        assertTrue(calculatorSchema.contains("operation"));
        assertTrue(calculatorSchema.contains("required"));

        String dateSchema = callbacks.get("calculateDaysBetween").getToolDefinition().inputSchema();
        assertTrue(dateSchema.contains("startDate"));
        assertTrue(dateSchema.contains("endDate"));

        String dateResult = callbacks.get("getCurrentDate").call(
                "{}", new ToolContext(Map.of(ToolContextKeys.REQUEST_ID, "schema-test")));
        assertTrue(dateResult.contains("2026-08-21"));
        assertTrue(dateResult.contains("FRIDAY"));
    }
}
