package com.zorth.aiplatform.core.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zorth.aiplatform.core.exception.AiException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class SpringAiChatServiceTest {

    @Mock
    private ChatModel chatModel;

    private ChatMemory chatMemory;
    private SpringAiChatService service;

    @BeforeEach
    void setUp() {
        lenient().when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
        service = new SpringAiChatService(
                ChatClient.builder(chatModel).build(), chatMemory, () -> "generated-conv");
    }

    @Test
    void returnsGeneratedContentAndConversationId() {
        when(chatModel.call(any(Prompt.class))).thenReturn(response("generated answer"));

        ChatResponse result = service.chat(new ChatRequest("question"));

        assertEquals("generated answer", result.content());
        assertEquals("generated-conv", result.conversationId());
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void usesClientConversationId() {
        when(chatModel.call(any(Prompt.class))).thenReturn(response("ok"));

        ChatResponse result = service.chat(new ChatRequest("question", "conv-42"));

        assertEquals("conv-42", result.conversationId());
        assertEquals(List.of("question", "ok"), texts(chatMemory.get("conv-42")));
    }

    @Test
    void continuesConversationUsingMemory() {
        List<Prompt> prompts = new ArrayList<>();
        AtomicInteger turn = new AtomicInteger();
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            prompts.add(prompt);
            return response(turn.getAndIncrement() == 0 ? "I am an assistant." : "Your name is 小明.");
        });

        service.chat(new ChatRequest("My name is 小明.", "conv-1"));
        ChatResponse second = service.chat(new ChatRequest("What is my name?", "conv-1"));

        assertEquals("Your name is 小明.", second.content());
        assertEquals("conv-1", second.conversationId());
        assertEquals(2, prompts.size());
        assertEquals(List.of("My name is 小明."), texts(prompts.get(0).getInstructions()));
        assertEquals(
                List.of("My name is 小明.", "I am an assistant.", "What is my name?"),
                texts(prompts.get(1).getInstructions()));
    }

    @Test
    void isolatesConversations() {
        when(chatModel.call(any(Prompt.class))).thenReturn(response("ok"));

        service.chat(new ChatRequest("secret from A", "conv-a"));
        service.chat(new ChatRequest("hello from B", "conv-b"));

        assertEquals(List.of("secret from A", "ok"), texts(chatMemory.get("conv-a")));
        assertEquals(List.of("hello from B", "ok"), texts(chatMemory.get("conv-b")));
    }

    @Test
    void streamsTokensThenCompletesAndStoresMemory() {
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                response("你"),
                response("好")));

        List<ChatStreamEvent> events = service.stream(new ChatRequest("hi", "conv-stream"))
                .collectList()
                .block(Duration.ofSeconds(2));

        assertEquals(List.of(
                ChatStreamEvent.start("conv-stream"),
                ChatStreamEvent.delta("你"),
                ChatStreamEvent.delta("好"),
                ChatStreamEvent.completed("conv-stream")), events);
        assertEquals(List.of("hi", "你好"), texts(chatMemory.get("conv-stream")));
    }

    @Test
    void streamFailureEmitsSanitizedErrorEvent() {
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.error(new IllegalStateException("secret provider detail")));

        List<ChatStreamEvent> events = service.stream(new ChatRequest("hi", "conv-err"))
                .collectList()
                .block(Duration.ofSeconds(2));

        assertEquals(List.of(
                ChatStreamEvent.start("conv-err"),
                ChatStreamEvent.error()), events);
        assertTrue(events.stream().noneMatch(event ->
                event.message() != null && event.message().contains("secret provider detail")));
    }

    @Test
    void translatesModelFailure() {
        var failure = new IllegalStateException("provider failure");
        when(chatModel.call(any(Prompt.class))).thenThrow(failure);

        AiException exception = assertThrows(
                AiException.class,
                () -> service.chat(new ChatRequest("question")));
        assertEquals("AI model invocation failed", exception.getMessage());
        assertSame(failure, exception.getCause());

        verify(chatModel).call(any(Prompt.class));
    }

    private static org.springframework.ai.chat.model.ChatResponse response(String content) {
        return new org.springframework.ai.chat.model.ChatResponse(
                List.of(new Generation(new AssistantMessage(content))));
    }

    private static List<String> texts(List<Message> messages) {
        return messages.stream().map(Message::getText).toList();
    }
}
