package com.zorth.aiplatform.server.controller;

import com.zorth.aiplatform.agent.AgentRequest;
import com.zorth.aiplatform.agent.AgentResponse;
import com.zorth.aiplatform.agent.AgentRuntimeContext;
import com.zorth.aiplatform.agent.AiAgentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
    public AgentResponse execute(
            @Valid @RequestBody AgentRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
            String authorization) {
        return aiAgentService.execute(request, new AgentRuntimeContext(blankToNull(authorization)));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
