package com.zorth.aiplatform.agent.support;

import org.springframework.ai.chat.model.ToolContext;

public final class AgentContext {

    private AgentContext() {
    }

    public static String requestId(ToolContext toolContext) {
        return text(toolContext, ToolContextKeys.REQUEST_ID);
    }

    public static String conversationId(ToolContext toolContext) {
        return text(toolContext, ToolContextKeys.CONVERSATION_ID);
    }

    public static String userId(ToolContext toolContext) {
        return text(toolContext, ToolContextKeys.USER_ID);
    }

    public static String datasourceId(ToolContext toolContext) {
        return text(toolContext, ToolContextKeys.DATASOURCE_ID);
    }

    private static String text(ToolContext toolContext, String key) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object value = toolContext.getContext().get(key);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        return null;
    }
}
