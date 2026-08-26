package com.zorth.aiplatform.agent;

import com.zorth.aiplatform.agent.conversation.AgentConversationMemory;
import com.zorth.aiplatform.agent.conversation.AgentStoredMessage;
import com.zorth.aiplatform.agent.conversation.AgentToolSummary;
import com.zorth.aiplatform.agent.conversation.AgentTurnAppend;
import com.zorth.aiplatform.agent.support.ToolContextKeys;
import com.zorth.aiplatform.agent.support.ToolExecutionSupport;
import com.zorth.aiplatform.agent.tool.CalculatorTools;
import com.zorth.aiplatform.agent.tool.DatabaseTools;
import com.zorth.aiplatform.agent.tool.DateTools;
import com.zorth.aiplatform.agent.tool.SystemTools;
import com.zorth.aiplatform.core.exception.AiClientException;
import com.zorth.aiplatform.core.exception.AiException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.core.io.Resource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

public final class SpringAiAgentService implements AiAgentService {

    private static final Logger log = LoggerFactory.getLogger(SpringAiAgentService.class);
    private static final int DEFAULT_MEMORY_MAX_MESSAGES = 20;
    private static final String EDITOR_CONTEXT_PREAMBLE =
            "The following is the current editor context for this turn only:\n";

    private final ChatClient chatClient;
    private final ToolCallingAdvisor toolCallingAdvisor;
    private final String systemPrompt;
    private final String databaseSystemPrompt;
    private final DateTools dateTools;
    private final CalculatorTools calculatorTools;
    private final SystemTools systemTools;
    private final DatabaseTools databaseTools;
    private final ToolExecutionSupport executionSupport;
    private final Supplier<String> requestIdSupplier;
    private final AgentConversationMemory conversationMemory;
    private final int memoryMaxMessages;
    private final Supplier<String> conversationIdSupplier;

    public SpringAiAgentService(
            ChatClient chatClient,
            ToolCallingAdvisor toolCallingAdvisor,
            Resource systemPrompt,
            DateTools dateTools,
            CalculatorTools calculatorTools,
            SystemTools systemTools) {
        this(chatClient, toolCallingAdvisor, systemPrompt, null, dateTools, calculatorTools,
                systemTools, null, new ToolExecutionSupport(), () -> UUID.randomUUID().toString(),
                null, DEFAULT_MEMORY_MAX_MESSAGES, () -> UUID.randomUUID().toString());
    }

    public SpringAiAgentService(
            ChatClient chatClient,
            ToolCallingAdvisor toolCallingAdvisor,
            Resource systemPrompt,
            Resource databaseSystemPrompt,
            DateTools dateTools,
            CalculatorTools calculatorTools,
            SystemTools systemTools,
            DatabaseTools databaseTools) {
        this(chatClient, toolCallingAdvisor, systemPrompt, databaseSystemPrompt, dateTools,
                calculatorTools, systemTools, databaseTools, new ToolExecutionSupport(),
                () -> UUID.randomUUID().toString(), null, DEFAULT_MEMORY_MAX_MESSAGES,
                () -> UUID.randomUUID().toString());
    }

    public SpringAiAgentService(
            ChatClient chatClient,
            ToolCallingAdvisor toolCallingAdvisor,
            Resource systemPrompt,
            Resource databaseSystemPrompt,
            DateTools dateTools,
            CalculatorTools calculatorTools,
            SystemTools systemTools,
            DatabaseTools databaseTools,
            ToolExecutionSupport executionSupport) {
        this(chatClient, toolCallingAdvisor, systemPrompt, databaseSystemPrompt, dateTools,
                calculatorTools, systemTools, databaseTools, executionSupport,
                () -> UUID.randomUUID().toString(), null, DEFAULT_MEMORY_MAX_MESSAGES,
                () -> UUID.randomUUID().toString());
    }

    SpringAiAgentService(
            ChatClient chatClient,
            ToolCallingAdvisor toolCallingAdvisor,
            Resource systemPrompt,
            DateTools dateTools,
            CalculatorTools calculatorTools,
            SystemTools systemTools,
            Supplier<String> requestIdSupplier) {
        this(chatClient, toolCallingAdvisor, systemPrompt, null, dateTools, calculatorTools,
                systemTools, null, new ToolExecutionSupport(), requestIdSupplier, null,
                DEFAULT_MEMORY_MAX_MESSAGES, () -> UUID.randomUUID().toString());
    }

    SpringAiAgentService(
            ChatClient chatClient,
            ToolCallingAdvisor toolCallingAdvisor,
            Resource systemPrompt,
            Resource databaseSystemPrompt,
            DateTools dateTools,
            CalculatorTools calculatorTools,
            SystemTools systemTools,
            DatabaseTools databaseTools,
            Supplier<String> requestIdSupplier) {
        this(chatClient, toolCallingAdvisor, systemPrompt, databaseSystemPrompt, dateTools,
                calculatorTools, systemTools, databaseTools, new ToolExecutionSupport(),
                requestIdSupplier, null, DEFAULT_MEMORY_MAX_MESSAGES,
                () -> UUID.randomUUID().toString());
    }

    SpringAiAgentService(
            ChatClient chatClient,
            ToolCallingAdvisor toolCallingAdvisor,
            Resource systemPrompt,
            Resource databaseSystemPrompt,
            DateTools dateTools,
            CalculatorTools calculatorTools,
            SystemTools systemTools,
            DatabaseTools databaseTools,
            ToolExecutionSupport executionSupport,
            Supplier<String> requestIdSupplier) {
        this(chatClient, toolCallingAdvisor, systemPrompt, databaseSystemPrompt, dateTools,
                calculatorTools, systemTools, databaseTools, executionSupport, requestIdSupplier,
                null, DEFAULT_MEMORY_MAX_MESSAGES, () -> UUID.randomUUID().toString());
    }

    public SpringAiAgentService(
            ChatClient chatClient,
            ToolCallingAdvisor toolCallingAdvisor,
            Resource systemPrompt,
            Resource databaseSystemPrompt,
            DateTools dateTools,
            CalculatorTools calculatorTools,
            SystemTools systemTools,
            DatabaseTools databaseTools,
            ToolExecutionSupport executionSupport,
            AgentConversationMemory conversationMemory,
            int memoryMaxMessages) {
        this(chatClient, toolCallingAdvisor, systemPrompt, databaseSystemPrompt, dateTools,
                calculatorTools, systemTools, databaseTools, executionSupport,
                () -> UUID.randomUUID().toString(), conversationMemory, memoryMaxMessages,
                () -> UUID.randomUUID().toString());
    }

    SpringAiAgentService(
            ChatClient chatClient,
            ToolCallingAdvisor toolCallingAdvisor,
            Resource systemPrompt,
            Resource databaseSystemPrompt,
            DateTools dateTools,
            CalculatorTools calculatorTools,
            SystemTools systemTools,
            DatabaseTools databaseTools,
            ToolExecutionSupport executionSupport,
            Supplier<String> requestIdSupplier,
            AgentConversationMemory conversationMemory,
            int memoryMaxMessages,
            Supplier<String> conversationIdSupplier) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient must not be null");
        this.toolCallingAdvisor = Objects.requireNonNull(toolCallingAdvisor,
                "toolCallingAdvisor must not be null");
        this.systemPrompt = readPrompt(systemPrompt, "systemPrompt");
        this.databaseSystemPrompt = databaseSystemPrompt == null
                ? null
                : readPrompt(databaseSystemPrompt, "databaseSystemPrompt");
        this.dateTools = Objects.requireNonNull(dateTools, "dateTools must not be null");
        this.calculatorTools = Objects.requireNonNull(calculatorTools,
                "calculatorTools must not be null");
        this.systemTools = Objects.requireNonNull(systemTools, "systemTools must not be null");
        this.databaseTools = databaseTools;
        this.executionSupport = Objects.requireNonNull(executionSupport,
                "executionSupport must not be null");
        this.requestIdSupplier = Objects.requireNonNull(requestIdSupplier,
                "requestIdSupplier must not be null");
        this.conversationMemory = conversationMemory;
        if (memoryMaxMessages < 1) {
            throw new IllegalArgumentException("memoryMaxMessages must be at least 1");
        }
        this.memoryMaxMessages = memoryMaxMessages;
        this.conversationIdSupplier = Objects.requireNonNull(conversationIdSupplier,
                "conversationIdSupplier must not be null");
    }

    @Override
    public AgentResponse execute(AgentRequest request) {
        return execute(request, AgentRuntimeContext.none());
    }

    @Override
    public AgentResponse execute(AgentRequest request, AgentRuntimeContext runtime) {
        Objects.requireNonNull(request, "request must not be null");
        AgentRuntimeContext safeRuntime = runtime == null ? AgentRuntimeContext.none() : runtime;

        String requestId = requestIdSupplier.get();
        long startedAt = System.nanoTime();
        boolean databaseRequest = request.datasourceId() != null && databaseTools != null;
        AgentSession session = prepareSession(request, safeRuntime);
        log.info("Agent request started requestId={} conversationId={} datasourceId={} database={} status=STARTED",
                requestId, value(session.conversationId()), value(request.datasourceId()),
                value(request.database()));

        List<AgentToolSummary> tools = new CopyOnWriteArrayList<>();
        try (AutoCloseable subscription = executionSupport.listen(requestId,
                (toolName, status) -> tools.add(new AgentToolSummary(toolName, status)))) {
            String content = prompt(request, safeRuntime, session, requestId, databaseRequest)
                    .call()
                    .content();

            if (content == null) {
                throw new AiException("AI agent returned no content");
            }

            persistTurn(session, request, content, tools);
            log.info("Agent request completed requestId={} durationMs={} status=SUCCESS",
                    requestId, elapsedMillis(startedAt));
            return new AgentResponse(content, session.conversationId());
        }
        catch (AiClientException exception) {
            log.error("Agent request failed requestId={} durationMs={} status=FAILURE",
                    requestId, elapsedMillis(startedAt), exception);
            throw exception;
        }
        catch (AiException exception) {
            log.error("Agent request failed requestId={} durationMs={} status=FAILURE",
                    requestId, elapsedMillis(startedAt), exception);
            throw exception;
        }
        catch (RuntimeException exception) {
            log.error("Agent request failed requestId={} durationMs={} status=FAILURE",
                    requestId, elapsedMillis(startedAt), exception);
            throw new AiException("AI agent execution failed", exception);
        }
        catch (Exception exception) {
            log.error("Agent request failed requestId={} durationMs={} status=FAILURE",
                    requestId, elapsedMillis(startedAt), exception);
            throw new AiException("AI agent execution failed", exception);
        }
    }

    @Override
    public Flux<AgentStreamEvent> stream(AgentRequest request) {
        return stream(request, AgentRuntimeContext.none());
    }

    @Override
    public Flux<AgentStreamEvent> stream(AgentRequest request, AgentRuntimeContext runtime) {
        Objects.requireNonNull(request, "request must not be null");
        AgentRuntimeContext safeRuntime = runtime == null ? AgentRuntimeContext.none() : runtime;

        AgentSession session;
        try {
            session = prepareSession(request, safeRuntime);
        }
        catch (AiClientException exception) {
            return Flux.just(AgentStreamEvent.error(exception.code(), exception.getMessage()));
        }

        String requestId = requestIdSupplier.get();
        String conversationId = session.conversationId();
        int messageLength = request.message() == null ? 0 : request.message().length();
        long startedAt = System.nanoTime();
        boolean databaseRequest = request.datasourceId() != null && databaseTools != null;
        log.info("Agent stream started requestId={} conversationId={} datasourceId={} database={} messageLength={}",
                requestId, value(conversationId), value(request.datasourceId()),
                value(request.database()), messageLength);

        return Flux.defer(() -> {
            Sinks.Many<AgentStreamEvent> toolSink = Sinks.many().unicast().onBackpressureBuffer();
            StringBuilder assistant = new StringBuilder();
            List<AgentToolSummary> tools = new CopyOnWriteArrayList<>();
            AutoCloseable subscription = executionSupport.listen(requestId, (toolName, status) -> {
                tools.add(new AgentToolSummary(toolName, status));
                emitTool(toolSink, toolName, status);
            });
            Flux<AgentStreamEvent> toolsFlux = toolSink.asFlux();
            Flux<AgentStreamEvent> tokens = Flux.defer(() -> prompt(
                            request, safeRuntime, session, requestId, databaseRequest)
                    .stream()
                    .content()
                    .filter(chunk -> chunk != null && !chunk.isEmpty())
                    .map(chunk -> {
                        assistant.append(chunk);
                        return AgentStreamEvent.delta(chunk);
                    }))
                    .subscribeOn(Schedulers.boundedElastic())
                    .doFinally(signal -> {
                        closeQuietly(subscription);
                        toolSink.tryEmitComplete();
                    });
            Flux<AgentStreamEvent> live = Flux.merge(toolsFlux, tokens);
            Mono<AgentStreamEvent> completed = Mono.fromRunnable(
                            () -> persistTurn(session, request, assistant.toString(), tools))
                    .then(Mono.just(AgentStreamEvent.completed(conversationId)));
            return Flux.concat(
                    Mono.just(AgentStreamEvent.start(conversationId)),
                    live,
                    completed);
        })
                .doOnComplete(() -> log.info(
                        "Agent stream succeeded requestId={} durationMs={} status=SUCCESS",
                        requestId, elapsedMillis(startedAt)))
                .doOnError(ex -> log.error(
                        "Agent stream failed requestId={} durationMs={} status=FAILURE",
                        requestId, elapsedMillis(startedAt), ex))
                .onErrorResume(ex -> Flux.just(toStreamError(ex)));
    }

    private AgentSession prepareSession(AgentRequest request, AgentRuntimeContext runtime) {
        String conversationId = request.conversationId() != null
                ? request.conversationId()
                : conversationIdSupplier.get();
        String userId = runtime.userId();
        List<AgentStoredMessage> history = List.of();
        if (userId != null && conversationMemory != null) {
            conversationMemory.ensureOwned(userId, conversationId);
            history = conversationMemory.loadRecentMessages(userId, conversationId, memoryMaxMessages);
        }
        return new AgentSession(conversationId, userId, history);
    }

    private void persistTurn(
            AgentSession session,
            AgentRequest request,
            String assistantContent,
            List<AgentToolSummary> tools) {
        if (conversationMemory == null || session.userId() == null) {
            return;
        }
        conversationMemory.appendTurn(new AgentTurnAppend(
                session.userId(),
                session.conversationId(),
                visibleUserText(request),
                assistantContent,
                List.copyOf(tools),
                request.datasourceId(),
                request.database()));
    }

    private ChatClient.ChatClientRequestSpec prompt(
            AgentRequest request,
            AgentRuntimeContext runtime,
            AgentSession session,
            String requestId,
            boolean databaseRequest) {
        ChatClient.ChatClientRequestSpec spec = chatClient.prompt()
                .system(resolveSystemPrompt(databaseRequest, request));
        List<Message> history = toChatMessages(session.history());
        if (!history.isEmpty()) {
            spec = spec.messages(history);
        }
        return spec
                .user(visibleUserText(request))
                .tools(resolveTools(databaseRequest))
                .toolContext(toolContext(requestId, request, runtime, session.conversationId()))
                .advisors(toolCallingAdvisor);
    }

    private String resolveSystemPrompt(boolean databaseRequest, AgentRequest request) {
        String prompt = systemPrompt;
        if (databaseRequest && databaseSystemPrompt != null) {
            prompt = prompt + "\n\n" + databaseSystemPrompt;
        }
        if (request.userText() != null) {
            prompt = prompt + "\n\n" + EDITOR_CONTEXT_PREAMBLE + request.message();
        }
        return prompt;
    }

    private static String visibleUserText(AgentRequest request) {
        return request.userText() != null ? request.userText() : request.message();
    }

    private static List<Message> toChatMessages(List<AgentStoredMessage> history) {
        List<Message> messages = new ArrayList<>(history.size());
        for (AgentStoredMessage stored : history) {
            if (stored.assistant()) {
                messages.add(new AssistantMessage(stored.content()));
            }
            else {
                messages.add(new UserMessage(stored.content()));
            }
        }
        return messages;
    }

    private Object[] resolveTools(boolean databaseRequest) {
        if (databaseRequest) {
            return new Object[] {dateTools, calculatorTools, systemTools, databaseTools};
        }
        return new Object[] {dateTools, calculatorTools, systemTools};
    }

    private static Map<String, Object> toolContext(
            String requestId, AgentRequest request, AgentRuntimeContext runtime, String conversationId) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put(ToolContextKeys.REQUEST_ID, requestId);
        putIfPresent(context, ToolContextKeys.CONVERSATION_ID, conversationId);
        putIfPresent(context, ToolContextKeys.USER_ID, runtime.userId());
        putIfPresent(context, ToolContextKeys.DATASOURCE_ID, request.datasourceId());
        putIfPresent(context, ToolContextKeys.DATABASE, request.database());
        putIfPresent(context, ToolContextKeys.AUTHORIZATION, runtime.authorization());
        return context;
    }

    private static void putIfPresent(Map<String, Object> context, String key, String value) {
        if (value != null) {
            context.put(key, value);
        }
    }

    private static AgentStreamEvent toStreamError(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof AiClientException client) {
                return AgentStreamEvent.error(client.code(), client.getMessage());
            }
            current = current.getCause();
        }
        return AgentStreamEvent.error();
    }

    private static void emitTool(Sinks.Many<AgentStreamEvent> sink, String toolName, String status) {
        Sinks.EmitResult result = sink.tryEmitNext(AgentStreamEvent.tool(toolName, status));
        if (result.isFailure()) {
            log.debug("Dropped tool stream event toolName={} status={} result={}",
                    toolName, status, result);
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        }
        catch (Exception exception) {
            log.debug("Failed to close tool execution listener", exception);
        }
    }

    private static String readPrompt(Resource resource, String name) {
        Objects.requireNonNull(resource, name + " must not be null");
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        }
        catch (IOException exception) {
            throw new IllegalStateException("Failed to read " + name, exception);
        }
    }

    private static String value(String text) {
        return text == null ? "-" : text;
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private record AgentSession(
            String conversationId, String userId, List<AgentStoredMessage> history) {
    }
}
