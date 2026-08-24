package com.zorth.aiplatform.agent.support;

import com.zorth.aiplatform.agent.exception.ToolExecutionException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;

public final class ToolExecutionSupport {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutionSupport.class);
    private static final String UNKNOWN_REQUEST_ID = "unknown";

    public <T> T execute(String toolName, ToolContext toolContext, Supplier<T> operation) {
        Objects.requireNonNull(toolName, "toolName must not be null");
        Objects.requireNonNull(operation, "operation must not be null");

        String requestId = requestId(toolContext);
        long startedAt = System.nanoTime();
        log.info("Tool execution started requestId={} toolName={} status=STARTED",
                requestId, toolName);

        try {
            T result = operation.get();
            log.info("Tool execution completed requestId={} toolName={} durationMs={} status=SUCCESS",
                    requestId, toolName, elapsedMillis(startedAt));
            return result;
        }
        catch (ToolExecutionException exception) {
            log.error("Tool execution failed requestId={} toolName={} durationMs={} status=FAILURE",
                    requestId, toolName, elapsedMillis(startedAt), exception);
            throw exception;
        }
        catch (RuntimeException exception) {
            log.error("Tool execution failed requestId={} toolName={} durationMs={} status=FAILURE",
                    requestId, toolName, elapsedMillis(startedAt), exception);
            throw new ToolExecutionException("Tool execution failed: " + toolName, exception);
        }
    }

    public String requestId(ToolContext toolContext) {
        if (toolContext == null) {
            return UNKNOWN_REQUEST_ID;
        }
        Object value = toolContext.getContext().get(ToolContextKeys.REQUEST_ID);
        return value instanceof String requestId && !requestId.isBlank()
                ? requestId
                : UNKNOWN_REQUEST_ID;
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
