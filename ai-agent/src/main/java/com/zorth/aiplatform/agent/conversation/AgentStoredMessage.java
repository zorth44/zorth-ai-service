package com.zorth.aiplatform.agent.conversation;

public record AgentStoredMessage(String role, String content) {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    public boolean user() {
        return ROLE_USER.equals(role);
    }

    public boolean assistant() {
        return ROLE_ASSISTANT.equals(role);
    }
}
