package com.zorth.aiplatform.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.zorth.aiplatform.agent.support.ToolContextKeys;
import com.zorth.aiplatform.agent.support.ToolExecutionSupport;
import com.zorth.aiplatform.agent.tool.CalculatorTools;
import com.zorth.aiplatform.agent.tool.DatabaseTools;
import com.zorth.aiplatform.agent.tool.DateTools;
import com.zorth.aiplatform.agent.tool.SystemTools;
import com.zorth.aiplatform.core.exception.AiException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import reactor.core.publisher.Flux;

class SpringAiAgentServiceTest {

    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec responseSpec;
    private ChatClient.StreamResponseSpec streamSpec;
    private ToolCallingAdvisor toolCallingAdvisor;
    private Resource systemPrompt;
    private DateTools dateTools;
    private CalculatorTools calculatorTools;
    private SystemTools systemTools;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
        responseSpec = mock(ChatClient.CallResponseSpec.class);
        streamSpec = mock(ChatClient.StreamResponseSpec.class);
        toolCallingAdvisor = ToolCallingAdvisor.builder().build();
        systemPrompt = new ClassPathResource("prompts/agent-system-prompt.txt");
        dateTools = mock(DateTools.class);
        calculatorTools = mock(CalculatorTools.class);
        systemTools = mock(SystemTools.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
    }

    @Test
    void mapsFinalContentAndBuildsServerControlledToolContext() {
        when(responseSpec.content()).thenReturn("final answer");
        SpringAiAgentService service = serviceWithRequestId("request-123");

        AgentResponse response = service.execute(new AgentRequest("question"));

        assertEquals("final answer", response.content());
        verify(requestSpec).system(org.mockito.ArgumentMatchers.contains("You are an AI assistant"));
        verify(requestSpec).user("question");
        verify(requestSpec).tools(dateTools, calculatorTools, systemTools);
        verify(requestSpec).toolContext(argThat(context ->
                "request-123".equals(context.get(ToolContextKeys.REQUEST_ID))
                        && context.containsKey(ToolContextKeys.CONVERSATION_ID)
                        && context.size() == 2));
        verify(requestSpec).advisors(same(toolCallingAdvisor));
    }

    @Test
    void attachesDatabaseToolsAndServerControlledDatasourceContext() {
        when(responseSpec.content()).thenReturn("order total is 10000");
        DatabaseTools databaseTools = mock(DatabaseTools.class);
        SpringAiAgentService service = new SpringAiAgentService(
                chatClient,
                toolCallingAdvisor,
                systemPrompt,
                new ClassPathResource("prompts/database-agent-system-prompt.txt"),
                dateTools,
                calculatorTools,
                systemTools,
                databaseTools,
                () -> "request-123");

        AgentResponse response = service.execute(new AgentRequest(
                "查询今年每个月订单金额", "conv-1", "demo", "user-9", "orders"));

        assertEquals("order total is 10000", response.content());
        assertEquals("conv-1", response.conversationId());
        verify(requestSpec).system(org.mockito.ArgumentMatchers.contains("Database Tools"));
        verify(requestSpec).system(org.mockito.ArgumentMatchers.contains("fenced code block tagged sql"));
        verify(requestSpec).system(org.mockito.ArgumentMatchers.contains("Do not paste query result rows"));
        verify(requestSpec).tools(dateTools, calculatorTools, systemTools, databaseTools);
        verify(requestSpec).toolContext(argThat(context ->
                "request-123".equals(context.get(ToolContextKeys.REQUEST_ID))
                        && "conv-1".equals(context.get(ToolContextKeys.CONVERSATION_ID))
                        && !context.containsKey(ToolContextKeys.USER_ID)
                        && "demo".equals(context.get(ToolContextKeys.DATASOURCE_ID))
                        && "orders".equals(context.get(ToolContextKeys.DATABASE))
                        && !context.containsKey(ToolContextKeys.AUTHORIZATION)));
        verifyNoInteractions(databaseTools);
    }

    @Test
    void forwardsAuthorizationInMemoryAndKeepsItOffTheRequest() {
        when(responseSpec.content()).thenReturn("signed in");
        DatabaseTools databaseTools = mock(DatabaseTools.class);
        SpringAiAgentService service = new SpringAiAgentService(
                chatClient,
                toolCallingAdvisor,
                systemPrompt,
                new ClassPathResource("prompts/database-agent-system-prompt.txt"),
                dateTools,
                calculatorTools,
                systemTools,
                databaseTools,
                () -> "request-123");

        service.execute(
                new AgentRequest("查询订单", "conv-1", "demo", "spoof", "orders"),
                new AgentRuntimeContext("Bearer secret-token", "1001"));

        verify(requestSpec).system(org.mockito.ArgumentMatchers.contains("Numeric values"));
        verify(requestSpec).toolContext(argThat(context ->
                "Bearer secret-token".equals(context.get(ToolContextKeys.AUTHORIZATION))
                        && "1001".equals(context.get(ToolContextKeys.USER_ID))
                        && !"spoof".equals(context.get(ToolContextKeys.USER_ID))));
    }

    @Test
    void permitsFinalAnswerWithoutExecutingATool() {
        when(responseSpec.content()).thenReturn("Redis is an in-memory data store.");

        AgentResponse response = serviceWithRequestId("no-tool-request")
                .execute(new AgentRequest("Explain Redis"));

        assertEquals("Redis is an in-memory data store.", response.content());
        verifyNoInteractions(dateTools, calculatorTools, systemTools);
    }

    @Test
    void translatesModelOrToolBoundaryFailureAndNextRequestCanSucceed() {
        when(requestSpec.call())
                .thenThrow(new IllegalStateException("internal provider or tool detail"))
                .thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("recovered response");
        SpringAiAgentService service = serviceWithRequestId("request-123");

        AiException failure = assertThrows(AiException.class,
                () -> service.execute(new AgentRequest("first request")));
        assertEquals("AI agent execution failed", failure.getMessage());

        AgentResponse recovered = service.execute(new AgentRequest("second request"));
        assertEquals("recovered response", recovered.content());
    }

    @Test
    void rejectsMissingFinalContent() {
        when(responseSpec.content()).thenReturn(null);

        assertThrows(AiException.class,
                () -> serviceWithRequestId("request-123")
                        .execute(new AgentRequest("question")));
    }

    @Test
    void streamsTokensThenCompletes() {
        when(streamSpec.content()).thenReturn(Flux.just("你", "好"));

        List<AgentStreamEvent> events = serviceWithRequestId("request-123")
                .stream(new AgentRequest("hi", "conv-stream", null, null, null))
                .collectList()
                .block(Duration.ofSeconds(2));

        assertEquals(List.of(
                AgentStreamEvent.start("conv-stream"),
                AgentStreamEvent.delta("你"),
                AgentStreamEvent.delta("好"),
                AgentStreamEvent.completed("conv-stream")), events);
        verify(requestSpec).stream();
    }

    @Test
    void emitsToolEventsDuringStreamWithoutToolArguments() {
        ToolExecutionSupport executionSupport = new ToolExecutionSupport();
        when(streamSpec.content()).thenReturn(Flux.defer(() -> {
            executionSupport.execute(
                    "listTables",
                    new ToolContext(Map.of(ToolContextKeys.REQUEST_ID, "request-123")),
                    () -> "ok");
            return Flux.just("done");
        }));
        SpringAiAgentService service = new SpringAiAgentService(
                chatClient,
                toolCallingAdvisor,
                systemPrompt,
                null,
                dateTools,
                calculatorTools,
                systemTools,
                null,
                executionSupport,
                () -> "request-123");

        List<AgentStreamEvent> events = service
                .stream(new AgentRequest("列出表", "conv-1", "demo", "user-9", "orders"))
                .collectList()
                .block(Duration.ofSeconds(2));

        assertEquals(List.of(
                AgentStreamEvent.start("conv-1"),
                AgentStreamEvent.tool("listTables", AgentStreamEvent.STATUS_STARTED),
                AgentStreamEvent.tool("listTables", AgentStreamEvent.STATUS_SUCCESS),
                AgentStreamEvent.delta("done"),
                AgentStreamEvent.completed("conv-1")), events);
        assertTrue(events.stream().noneMatch(event ->
                event.content() != null && event.content().contains("ok")));
    }

    @Test
    void streamFailureEmitsSanitizedErrorEvent() {
        when(streamSpec.content())
                .thenReturn(Flux.error(new IllegalStateException("secret provider detail")));

        List<AgentStreamEvent> events = serviceWithRequestId("request-123")
                .stream(new AgentRequest("hi"))
                .collectList()
                .block(Duration.ofSeconds(2));

        assertEquals(AgentStreamEvent.TYPE_START, events.get(0).type());
        assertEquals(AgentStreamEvent.error(), events.get(1));
        assertTrue(events.stream().noneMatch(event ->
                event.message() != null && event.message().contains("secret provider detail")));
    }

    @Test
    void toolEventsAreEmittedBeforeModelTokensComplete() throws Exception {
        ToolExecutionSupport executionSupport = new ToolExecutionSupport();
        when(streamSpec.content()).thenReturn(Flux.<String>create(sink -> {
            executionSupport.execute(
                    "listTables",
                    new ToolContext(Map.of(ToolContextKeys.REQUEST_ID, "request-123")),
                    () -> "ok");
            try {
                Thread.sleep(250);
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                sink.error(exception);
                return;
            }
            sink.next("done");
            sink.complete();
        }));
        SpringAiAgentService service = new SpringAiAgentService(
                chatClient,
                toolCallingAdvisor,
                systemPrompt,
                null,
                dateTools,
                calculatorTools,
                systemTools,
                null,
                executionSupport,
                () -> "request-123");

        java.util.concurrent.CopyOnWriteArrayList<AgentStreamEvent> seen =
                new java.util.concurrent.CopyOnWriteArrayList<>();
        reactor.core.Disposable disposable = service
                .stream(new AgentRequest("列出表", "conv-1", "demo", "user-9", "orders"))
                .subscribe(seen::add);

        boolean toolsBeforeAnswer = false;
        for (int i = 0; i < 40; i++) {
            boolean hasTool = seen.stream().anyMatch(event -> AgentStreamEvent.TYPE_TOOL.equals(event.type()));
            boolean hasDelta = seen.stream().anyMatch(event -> AgentStreamEvent.TYPE_DELTA.equals(event.type()));
            if (hasTool && !hasDelta) {
                toolsBeforeAnswer = true;
                break;
            }
            Thread.sleep(25);
        }
        assertTrue(toolsBeforeAnswer);
        for (int i = 0; i < 40; i++) {
            if (seen.stream().anyMatch(event -> AgentStreamEvent.TYPE_COMPLETED.equals(event.type()))) {
                break;
            }
            Thread.sleep(25);
        }
        disposable.dispose();
    }

    private SpringAiAgentService serviceWithRequestId(String requestId) {
        return new SpringAiAgentService(
                chatClient,
                toolCallingAdvisor,
                systemPrompt,
                dateTools,
                calculatorTools,
                systemTools,
                () -> requestId);
    }
}
