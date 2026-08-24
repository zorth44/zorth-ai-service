package com.zorth.aiplatform.agent;

public interface AiAgentService {

    AgentResponse execute(AgentRequest request, AgentRuntimeContext runtime);

    default AgentResponse execute(AgentRequest request) {
        return execute(request, AgentRuntimeContext.none());
    }
}
