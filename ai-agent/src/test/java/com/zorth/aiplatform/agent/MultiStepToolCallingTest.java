package com.zorth.aiplatform.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zorth.aiplatform.agent.model.SystemInfo;
import com.zorth.aiplatform.agent.support.ToolExecutionSupport;
import com.zorth.aiplatform.agent.tool.CalculatorTools;
import com.zorth.aiplatform.agent.tool.DateTools;
import com.zorth.aiplatform.agent.tool.SystemTools;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
import reactor.core.publisher.Flux;

class MultiStepToolCallingTest {

    @Test
    void springAiAdvisorExecutesSuccessiveToolsWithoutApplicationLoop() {
        ScriptedMultiStepChatModel chatModel = new ScriptedMultiStepChatModel();
        ToolExecutionSupport executionSupport = new ToolExecutionSupport();
        DateTools dateTools = new DateTools(
                Clock.fixed(Instant.parse("2026-08-21T12:00:00Z"), ZoneOffset.UTC),
                executionSupport);
        CalculatorTools calculatorTools = new CalculatorTools(executionSupport);
        SystemTools systemTools = new SystemTools(
                new SystemInfo("ai-platform", "test", "test-version"), executionSupport);
        SpringAiAgentService service = new SpringAiAgentService(
                ChatClient.create(chatModel),
                ToolCallingAdvisor.builder().build(),
                new ClassPathResource("prompts/agent-system-prompt.txt"),
                dateTools,
                calculatorTools,
                systemTools,
                () -> "multi-step-request");

        AgentResponse response = service.execute(
                new AgentRequest("How many days until 2027-01-01?"));

        assertEquals(3, chatModel.calls());
        assertEquals("There are 133 days until 2027-01-01.", response.content());
    }

    @Test
    void streamEmitsToolProgressThenFinalAnswer() {
        ScriptedMultiStepChatModel chatModel = new ScriptedMultiStepChatModel();
        ToolExecutionSupport executionSupport = new ToolExecutionSupport();
        DateTools dateTools = new DateTools(
                Clock.fixed(Instant.parse("2026-08-21T12:00:00Z"), ZoneOffset.UTC),
                executionSupport);
        CalculatorTools calculatorTools = new CalculatorTools(executionSupport);
        SystemTools systemTools = new SystemTools(
                new SystemInfo("ai-platform", "test", "test-version"), executionSupport);
        SpringAiAgentService service = new SpringAiAgentService(
                ChatClient.create(chatModel),
                ToolCallingAdvisor.builder().build(),
                new ClassPathResource("prompts/agent-system-prompt.txt"),
                null,
                dateTools,
                calculatorTools,
                systemTools,
                null,
                executionSupport,
                () -> "multi-step-stream");

        List<AgentStreamEvent> events = service
                .stream(new AgentRequest("How many days until 2027-01-01?"))
                .collectList()
                .block(Duration.ofSeconds(2));

        assertEquals(3, chatModel.calls());
        assertTrue(events.contains(AgentStreamEvent.tool("getCurrentDate", AgentStreamEvent.STATUS_STARTED)));
        assertTrue(events.contains(AgentStreamEvent.tool("getCurrentDate", AgentStreamEvent.STATUS_SUCCESS)));
        assertTrue(events.contains(AgentStreamEvent.tool("calculateDaysBetween", AgentStreamEvent.STATUS_STARTED)));
        assertTrue(events.contains(AgentStreamEvent.delta("There are 133 days until 2027-01-01.")));
        assertEquals(AgentStreamEvent.completed(null), events.get(events.size() - 1));
    }

    private static final class ScriptedMultiStepChatModel implements ChatModel {

        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public ChatResponse call(Prompt prompt) {
            int call = calls.incrementAndGet();
            return switch (call) {
                case 1 -> responseWithToolCall(
                        "date-call", "getCurrentDate", "{}");
                case 2 -> {
                    assertToolResult(prompt, "getCurrentDate", "2026-08-21");
                    yield responseWithToolCall(
                            "difference-call",
                            "calculateDaysBetween",
                            "{\"startDate\":\"2026-08-21\",\"endDate\":\"2027-01-01\"}");
                }
                case 3 -> {
                    assertToolResult(prompt, "calculateDaysBetween", "133");
                    yield responseWithText("There are 133 days until 2027-01-01.");
                }
                default -> throw new AssertionError("Unexpected model call " + call);
            };
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(call(prompt));
        }

        @Override
        public ChatOptions getOptions() {
            return ToolCallingChatOptions.builder().build();
        }

        int calls() {
            return calls.get();
        }

        private static ChatResponse responseWithToolCall(
                String id, String name, String arguments) {
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

            assertEquals(1, responses.size());
            assertTrue(responses.get(0).responseData().contains(content));
        }
    }
}
