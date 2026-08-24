package com.zorth.aiplatform.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zorth.aiplatform.agent.model.SystemInfo;
import com.zorth.aiplatform.agent.support.ToolExecutionSupport;
import com.zorth.aiplatform.agent.tool.CalculatorTools;
import com.zorth.aiplatform.agent.tool.DatabaseTools;
import com.zorth.aiplatform.agent.tool.DateTools;
import com.zorth.aiplatform.agent.tool.H2DatabaseToolFixture;
import com.zorth.aiplatform.agent.tool.SystemTools;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.core.io.ClassPathResource;

class DatabaseAgentMultiStepTest {

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
    void springAiExecutesListSchemaCheckAndQueryWithoutApplicationLoop() {
        ScriptedChatModel chatModel = new ScriptedChatModel(false);
        SpringAiAgentService service = service(chatModel, fixture.tools());

        AgentResponse response = service.execute(new AgentRequest(
                "查询今年每个月的订单金额", "conv-db", H2DatabaseToolFixture.DATASOURCE_ID, "user-1"));

        assertEquals(5, chatModel.calls());
        assertEquals("January 10000, February 20000, March 5000.", response.content());
        assertEquals("conv-db", response.conversationId());
    }

    @Test
    void failedQueryCanBeRepairedInTheSameRequest() {
        ScriptedChatModel chatModel = new ScriptedChatModel(true);
        SpringAiAgentService service = service(chatModel, fixture.tools());

        AgentResponse response = service.execute(new AgentRequest(
                "查询订单时间", "conv-repair", H2DatabaseToolFixture.DATASOURCE_ID, "user-1"));

        assertEquals(5, chatModel.calls());
        assertEquals("Orders use created_at, not order_time.", response.content());
    }

    private static SpringAiAgentService service(ChatModel chatModel, DatabaseTools databaseTools) {
        ToolExecutionSupport executionSupport = new ToolExecutionSupport();
        return new SpringAiAgentService(
                ChatClient.create(chatModel),
                ToolCallingAdvisor.builder().build(),
                new ClassPathResource("prompts/agent-system-prompt.txt"),
                new ClassPathResource("prompts/database-agent-system-prompt.txt"),
                new DateTools(Clock.fixed(Instant.parse("2026-08-21T12:00:00Z"), ZoneOffset.UTC),
                        executionSupport),
                new CalculatorTools(executionSupport),
                new SystemTools(new SystemInfo("ai-platform", "test", "test-version"),
                        executionSupport),
                databaseTools,
                () -> "db-multi-step");
    }

    private static final class ScriptedChatModel implements ChatModel {

        private final boolean repairPath;
        private final AtomicInteger calls = new AtomicInteger();

        private ScriptedChatModel(boolean repairPath) {
            this.repairPath = repairPath;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            int call = calls.incrementAndGet();
            if (repairPath) {
                return switch (call) {
                    case 1 -> responseWithToolCall(
                            "bad-query",
                            "executeQuery",
                            "{\"sql\":\"SELECT order_time FROM orders\"}");
                    case 2 -> {
                        assertToolResult(prompt, "executeQuery", "SQL_EXECUTION_ERROR");
                        yield responseWithToolCall(
                                "schema-call", "getTableSchema", "{\"tableNames\":\"orders\"}");
                    }
                    case 3 -> {
                        assertToolResult(prompt, "getTableSchema", "created_at");
                        yield responseWithToolCall(
                                "check-call",
                                "checkSql",
                                "{\"sql\":\"SELECT created_at FROM orders\"}");
                    }
                    case 4 -> {
                        assertToolResult(prompt, "checkSql", "true");
                        yield responseWithToolCall(
                                "good-query",
                                "executeQuery",
                                "{\"sql\":\"SELECT created_at FROM orders\"}");
                    }
                    case 5 -> {
                        assertToolResult(prompt, "executeQuery", "created_at");
                        yield responseWithText("Orders use created_at, not order_time.");
                    }
                    default -> throw new AssertionError("Unexpected model call " + call);
                };
            }
            return switch (call) {
                case 1 -> responseWithToolCall("tables-call", "listTables", "{}");
                case 2 -> {
                    assertToolResult(prompt, "listTables", "orders");
                    yield responseWithToolCall(
                            "schema-call", "getTableSchema", "{\"tableNames\":\"orders\"}");
                }
                case 3 -> {
                    assertToolResult(prompt, "getTableSchema", "amount");
                    yield responseWithToolCall(
                            "check-call",
                            "checkSql",
                            "{\"sql\":\"SELECT amount FROM orders\"}");
                }
                case 4 -> {
                    assertToolResult(prompt, "checkSql", "true");
                    yield responseWithToolCall(
                            "query-call",
                            "executeQuery",
                            "{\"sql\":\"SELECT amount FROM orders ORDER BY id\"}");
                }
                case 5 -> {
                    assertToolResult(prompt, "executeQuery", "10000");
                    yield responseWithText("January 10000, February 20000, March 5000.");
                }
                default -> throw new AssertionError("Unexpected model call " + call);
            };
        }

        @Override
        public ChatOptions getOptions() {
            return ToolCallingChatOptions.builder().build();
        }

        int calls() {
            return calls.get();
        }

        private static ChatResponse responseWithToolCall(String id, String name, String arguments) {
            AssistantMessage message = AssistantMessage.builder()
                    .content("")
                    .toolCalls(List.of(new AssistantMessage.ToolCall(
                            id, "function", name, arguments)))
                    .build();
            return new ChatResponse(List.of(new Generation(
                    message,
                    ChatGenerationMetadata.builder().finishReason("tool_calls").build())));
        }

        private static ChatResponse responseWithText(String text) {
            return new ChatResponse(List.of(new Generation(
                    new AssistantMessage(text),
                    ChatGenerationMetadata.builder().finishReason("stop").build())));
        }

        private static void assertToolResult(Prompt prompt, String toolName, String content) {
            List<ToolResponseMessage.ToolResponse> responses = prompt.getInstructions().stream()
                    .filter(ToolResponseMessage.class::isInstance)
                    .map(ToolResponseMessage.class::cast)
                    .map(ToolResponseMessage::getResponses)
                    .flatMap(List::stream)
                    .filter(response -> toolName.equals(response.name()))
                    .toList();
            assertFalse(responses.isEmpty());
            assertTrue(responses.get(responses.size() - 1).responseData().contains(content));
        }
    }
}
