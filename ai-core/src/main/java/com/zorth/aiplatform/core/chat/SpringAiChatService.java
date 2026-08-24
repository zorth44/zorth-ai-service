package com.zorth.aiplatform.core.chat;

import com.zorth.aiplatform.core.exception.AiException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

public final class SpringAiChatService implements AiChatService {

    private static final Logger log = LoggerFactory.getLogger(SpringAiChatService.class);

    private final ChatClient chatClient;

    public SpringAiChatService(ChatClient chatClient) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient must not be null");
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        int messageLength = request.message() == null ? 0 : request.message().length();
        long startedAt = System.nanoTime();
        log.info("AI chat request started messageLength={}", messageLength);

        try {
            String content = chatClient.prompt()
                    .user(request.message())
                    .call()
                    .content();

            if (content == null) {
                throw new AiException("AI model returned no content");
            }

            log.info("AI chat request succeeded messageLength={} durationMs={}",
                    messageLength, elapsedMillis(startedAt));
            return new ChatResponse(content);
        }
        catch (AiException ex) {
            log.error("AI chat request failed messageLength={} durationMs={}",
                    messageLength, elapsedMillis(startedAt), ex);
            throw ex;
        }
        catch (RuntimeException ex) {
            log.error("AI chat request failed messageLength={} durationMs={}",
                    messageLength, elapsedMillis(startedAt), ex);
            throw new AiException("AI model invocation failed", ex);
        }
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
