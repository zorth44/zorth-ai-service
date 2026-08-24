package com.zorth.aiplatform.agent.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;

public final class DatabaseToolAudit {

    private static final Logger log = LoggerFactory.getLogger(DatabaseToolAudit.class);
    private static final int MAX_ARGUMENT_LENGTH = 2_000;

    public void record(
            ToolContext toolContext,
            String toolName,
            String toolArguments,
            String executionId,
            String toolResultStatus,
            long durationMs,
            Integer queryRowCount,
            String errorMessage) {
        log.info("{}", render(
                toolContext,
                toolName,
                toolArguments,
                executionId,
                toolResultStatus,
                durationMs,
                queryRowCount,
                errorMessage));
    }

    String render(
            ToolContext toolContext,
            String toolName,
            String toolArguments,
            String executionId,
            String toolResultStatus,
            long durationMs,
            Integer queryRowCount,
            String errorMessage) {
        return "Database tool audit conversationId=" + value(AgentContext.conversationId(toolContext))
                + " userId=" + value(AgentContext.userId(toolContext))
                + " datasourceId=" + value(AgentContext.datasourceId(toolContext))
                + " database=" + value(AgentContext.database(toolContext))
                + " requestId=" + value(AgentContext.requestId(toolContext))
                + " executionId=" + value(executionId)
                + " toolName=" + toolName
                + " toolArguments=" + truncate(toolArguments)
                + " toolResultStatus=" + toolResultStatus
                + " durationMs=" + durationMs
                + " queryRowCount=" + (queryRowCount == null ? "-" : queryRowCount)
                + " errorMessage=" + value(errorMessage);
    }

    private static String value(String text) {
        return text == null || text.isBlank() ? "-" : text;
    }

    private static String truncate(String text) {
        if (text == null || text.isBlank()) {
            return "-";
        }
        return text.length() <= MAX_ARGUMENT_LENGTH
                ? text
                : text.substring(0, MAX_ARGUMENT_LENGTH) + "...";
    }
}
