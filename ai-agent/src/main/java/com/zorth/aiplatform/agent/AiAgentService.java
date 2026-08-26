package com.zorth.aiplatform.agent;

import reactor.core.publisher.Flux;

public interface AiAgentService {

    AgentResponse execute(AgentRequest request, AgentRuntimeContext runtime);

    Flux<AgentStreamEvent> stream(AgentRequest request, AgentRuntimeContext runtime);

    default AgentResponse execute(AgentRequest request) {
        return execute(request, AgentRuntimeContext.none());
    }

    default Flux<AgentStreamEvent> stream(AgentRequest request) {
        return stream(request, AgentRuntimeContext.none());
    }
}
