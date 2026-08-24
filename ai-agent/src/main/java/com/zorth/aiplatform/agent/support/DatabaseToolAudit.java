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
            String toolResultStatus,
            long durationMs,
            Integer queryRowCount,
            String errorMessage) {
        log.info(
                "Database tool audit conversationId={} userId={} datasourceId={} requestId={} "
                        + "toolName={} toolArguments={} toolResultStatus={} durationMs={} "
                        + "queryRowCount={} errorMessage={}",
                value(AgentContext.conversationId(toolContext)),
                value(AgentContext.userId(toolContext)),
                value(AgentContext.datasourceId(toolContext)),
                value(AgentContext.requestId(toolContext)),
                toolName,
                truncate(toolArguments),
                toolResultStatus,
                durationMs,
                queryRowCount == null ? "-" : queryRowCount,
                value(errorMessage));
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
