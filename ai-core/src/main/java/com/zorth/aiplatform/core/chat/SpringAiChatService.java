package com.zorth.aiplatform.core.chat;

import com.zorth.aiplatform.core.exception.AiException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class SpringAiChatService implements AiChatService {

    private static final Logger log = LoggerFactory.getLogger(SpringAiChatService.class);

    private final ChatClient chatClient;
    private final MessageChatMemoryAdvisor memoryAdvisor;
    private final Supplier<String> conversationIdSupplier;

    public SpringAiChatService(ChatClient chatClient, ChatMemory chatMemory) {
        this(chatClient, chatMemory, () -> UUID.randomUUID().toString());
    }

    SpringAiChatService(
            ChatClient chatClient, ChatMemory chatMemory, Supplier<String> conversationIdSupplier) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient must not be null");
        Objects.requireNonNull(chatMemory, "chatMemory must not be null");
        this.memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        this.conversationIdSupplier = Objects.requireNonNull(
                conversationIdSupplier, "conversationIdSupplier must not be null");
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        String conversationId = resolveConversationId(request);
        int messageLength = request.message() == null ? 0 : request.message().length();
        long startedAt = System.nanoTime();
        log.info("AI chat request started conversationId={} messageLength={}",
                conversationId, messageLength);

        try {
            String content = prompt(request, conversationId)
                    .call()
                    .content();

            if (content == null) {
                throw new AiException("AI model returned no content");
            }

            log.info("AI chat request succeeded conversationId={} messageLength={} durationMs={}",
                    conversationId, messageLength, elapsedMillis(startedAt));
            return new ChatResponse(content, conversationId);
        }
        catch (AiException ex) {
            log.error("AI chat request failed conversationId={} messageLength={} durationMs={}",
                    conversationId, messageLength, elapsedMillis(startedAt), ex);
            throw ex;
        }
        catch (RuntimeException ex) {
            log.error("AI chat request failed conversationId={} messageLength={} durationMs={}",
                    conversationId, messageLength, elapsedMillis(startedAt), ex);
            throw new AiException("AI model invocation failed", ex);
        }
    }

    @Override
    public Flux<ChatStreamEvent> stream(ChatRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        String conversationId = resolveConversationId(request);
        int messageLength = request.message() == null ? 0 : request.message().length();
        long startedAt = System.nanoTime();
        log.info("AI chat stream started conversationId={} messageLength={}",
                conversationId, messageLength);

        Flux<ChatStreamEvent> tokens = prompt(request, conversationId)
                .stream()
                .content()
                .filter(chunk -> chunk != null && !chunk.isEmpty())
                .map(ChatStreamEvent::delta)
                .concatWith(Mono.just(ChatStreamEvent.completed(conversationId)));

        return Flux.concat(Mono.just(ChatStreamEvent.start(conversationId)), tokens)
                .doOnComplete(() -> log.info(
                        "AI chat stream succeeded conversationId={} messageLength={} durationMs={}",
                        conversationId, messageLength, elapsedMillis(startedAt)))
                .doOnError(ex -> log.error(
                        "AI chat stream failed conversationId={} messageLength={} durationMs={}",
                        conversationId, messageLength, elapsedMillis(startedAt), ex))
                .onErrorResume(ex -> Flux.just(ChatStreamEvent.error()));
    }

    private ChatClient.ChatClientRequestSpec prompt(ChatRequest request, String conversationId) {
        return chatClient.prompt()
                .user(request.message())
                .advisors(memoryAdvisor)
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId));
    }

    private String resolveConversationId(ChatRequest request) {
        return request.conversationId() != null
                ? request.conversationId()
                : conversationIdSupplier.get();
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
