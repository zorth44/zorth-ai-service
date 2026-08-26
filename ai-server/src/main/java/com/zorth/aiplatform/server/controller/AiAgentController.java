package com.zorth.aiplatform.server.controller;

import com.zorth.aiplatform.agent.AgentRequest;
import com.zorth.aiplatform.agent.AgentResponse;
import com.zorth.aiplatform.agent.AgentRuntimeContext;
import com.zorth.aiplatform.agent.AgentStreamEvent;
import com.zorth.aiplatform.agent.AiAgentService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

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
        return aiAgentService.execute(request, runtime(authorization));
    }

    @PostMapping(
            path = "/agent/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AgentStreamEvent>> stream(
            @Valid @RequestBody AgentRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
            String authorization,
            HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        response.setBufferSize(1);
        return aiAgentService.stream(request, runtime(authorization))
                .map(event -> ServerSentEvent.<AgentStreamEvent>builder()
                        .event(event.type())
                        .data(event)
                        .build())
                .onErrorResume(ex -> Flux.just(ServerSentEvent.<AgentStreamEvent>builder()
                        .event(AgentStreamEvent.TYPE_ERROR)
                        .data(AgentStreamEvent.error())
                        .build()));
    }

    private static AgentRuntimeContext runtime(String authorization) {
        return new AgentRuntimeContext(blankToNull(authorization));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
