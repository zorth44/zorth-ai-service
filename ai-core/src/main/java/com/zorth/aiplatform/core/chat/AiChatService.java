package com.zorth.aiplatform.core.chat;

import reactor.core.publisher.Flux;

public interface AiChatService {

    ChatResponse chat(ChatRequest request);

    Flux<ChatStreamEvent> stream(ChatRequest request);
}
