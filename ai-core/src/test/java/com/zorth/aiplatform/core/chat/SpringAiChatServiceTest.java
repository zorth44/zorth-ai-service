package com.zorth.aiplatform.core.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zorth.aiplatform.core.exception.AiException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

@ExtendWith(MockitoExtension.class)
class SpringAiChatServiceTest {

    @Mock
    private ChatModel chatModel;

    private SpringAiChatService service;

    @BeforeEach
    void setUp() {
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        service = new SpringAiChatService(ChatClient.builder(chatModel).build());
    }

    @Test
    void returnsGeneratedContent() {
        var modelResponse = new org.springframework.ai.chat.model.ChatResponse(
                List.of(new Generation(new AssistantMessage("generated answer"))));
        when(chatModel.call(any(Prompt.class))).thenReturn(modelResponse);

        ChatResponse response = service.chat(new ChatRequest("question"));

        assertEquals("generated answer", response.content());
        verify(chatModel).call(any(Prompt.class));
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
}
