package com.zorth.aiplatform.agent;

public record AgentRuntimeContext(String authorization, String userId) {

    public AgentRuntimeContext(String authorization) {
        this(authorization, null);
    }

    public static AgentRuntimeContext none() {
        return new AgentRuntimeContext(null, null);
    }
}
