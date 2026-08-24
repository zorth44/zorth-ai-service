package com.zorth.aiplatform.server.controller;

import com.zorth.aiplatform.agent.AgentRequest;
import com.zorth.aiplatform.agent.AgentResponse;
import com.zorth.aiplatform.agent.AiAgentService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiAgentController {

    private final AiAgentService aiAgentService;

    public AiAgentController(AiAgentService aiAgentService) {
        this.aiAgentService = aiAgentService;
    }

    @PostMapping(
            path = "/agent",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public AgentResponse execute(@Valid @RequestBody AgentRequest request) {
        return aiAgentService.execute(request);
    }
}
