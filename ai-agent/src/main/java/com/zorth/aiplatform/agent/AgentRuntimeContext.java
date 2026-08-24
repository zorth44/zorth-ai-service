package com.zorth.aiplatform.agent;

public record AgentRuntimeContext(String authorization) {

    public static AgentRuntimeContext none() {
        return new AgentRuntimeContext(null);
    }
}
