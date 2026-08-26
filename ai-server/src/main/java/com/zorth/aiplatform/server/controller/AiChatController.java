package com.zorth.aiplatform.server.controller;

import com.zorth.aiplatform.core.chat.AiChatService;
import com.zorth.aiplatform.core.chat.ChatRequest;
import com.zorth.aiplatform.core.chat.ChatResponse;
import com.zorth.aiplatform.core.chat.ChatStreamEvent;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/ai")
public class AiChatController {

    private final AiChatService aiChatService;

    public AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping(
            path = "/chat",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return aiChatService.chat(request);
    }

    @PostMapping(
            path = "/chat/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatStreamEvent>> stream(@Valid @RequestBody ChatRequest request) {
        return aiChatService.stream(request)
                .map(event -> ServerSentEvent.<ChatStreamEvent>builder()
                        .event(event.type())
                        .data(event)
                        .build())
                .onErrorResume(ex -> Flux.just(ServerSentEvent.<ChatStreamEvent>builder()
                        .event(ChatStreamEvent.TYPE_ERROR)
                        .data(ChatStreamEvent.error())
                        .build()));
    }
}
