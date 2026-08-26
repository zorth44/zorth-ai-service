package com.zorth.aiplatform.agent.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zorth.aiplatform.agent.exception.ToolExecutionException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;

class ToolExecutionSupportTest {

    private final ToolExecutionSupport executionSupport = new ToolExecutionSupport();

    @Test
    void readsServerGeneratedRequestIdFromToolContext() {
        ToolContext context = new ToolContext(Map.of(ToolContextKeys.REQUEST_ID, "request-123"));

        assertEquals("request-123", executionSupport.requestId(context));
    }

    @Test
    void doesNotSerializeResultForCommonLogging() {
        SensitiveValue value = new SensitiveValue();

        assertSame(value, executionSupport.execute(
                "safeTool",
                new ToolContext(Map.of(ToolContextKeys.REQUEST_ID, "request-123")),
                () -> value));
    }

    @Test
    void wrapsUnexpectedRuntimeFailureAndKeepsCause() {
        IllegalStateException cause = new IllegalStateException("server diagnostic");

        ToolExecutionException exception = assertThrows(ToolExecutionException.class,
                () -> executionSupport.execute(
                        "failingTool",
                        new ToolContext(Map.of(ToolContextKeys.REQUEST_ID, "request-123")),
                        () -> { throw cause; }));

        assertSame(cause, exception.getCause());
    }

    @Test
    void recordsCorrelatedSuccessAndFailureWithoutArgumentOrResultValues() {
        Logger logger = (Logger) LoggerFactory.getLogger(ToolExecutionSupport.class);
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            ToolContext context = new ToolContext(Map.of(
                    ToolContextKeys.REQUEST_ID, "observable-request"));
            executionSupport.execute("successfulTool", context, SensitiveValue::new);
            assertThrows(ToolExecutionException.class,
                    () -> executionSupport.execute(
                            "failedTool", context,
                            () -> { throw new IllegalStateException("sensitive-argument-value"); }));

            List<String> messages = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();
            assertTrue(messages.stream().anyMatch(message ->
                    message.contains("requestId=observable-request")
                            && message.contains("toolName=successfulTool")
                            && message.contains("durationMs=")
                            && message.contains("status=SUCCESS")));
            assertTrue(messages.stream().anyMatch(message ->
                    message.contains("requestId=observable-request")
                            && message.contains("toolName=failedTool")
                            && message.contains("durationMs=")
                            && message.contains("status=FAILURE")));
            assertTrue(messages.stream().noneMatch(message ->
                    message.contains("sensitive-argument-value")));
        }
        finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }

    @Test
    void notifiesListenerWithoutArgumentsOrResults() throws Exception {
        List<String> events = new java.util.ArrayList<>();
        ToolContext context = new ToolContext(Map.of(ToolContextKeys.REQUEST_ID, "request-123"));
        try (AutoCloseable ignored = executionSupport.listen(
                "request-123",
                (toolName, status) -> events.add(toolName + ":" + status))) {
            SensitiveValue value = new SensitiveValue();
            assertSame(value, executionSupport.execute("safeTool", context, () -> value));
            assertThrows(ToolExecutionException.class,
                    () -> executionSupport.execute(
                            "failedTool", context,
                            () -> { throw new IllegalStateException("sensitive-argument-value"); }));
        }

        assertEquals(List.of(
                "safeTool:STARTED",
                "safeTool:SUCCESS",
                "failedTool:STARTED",
                "failedTool:FAILURE"), events);
    }

    private static final class SensitiveValue {
        @Override
        public String toString() {
            throw new AssertionError("Common logging must not serialize tool results");
        }
    }
}
