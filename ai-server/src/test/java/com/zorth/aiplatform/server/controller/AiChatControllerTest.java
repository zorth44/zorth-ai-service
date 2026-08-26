package com.zorth.aiplatform.server.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zorth.aiplatform.core.chat.AiChatService;
import com.zorth.aiplatform.core.chat.ChatResponse;
import com.zorth.aiplatform.core.chat.ChatStreamEvent;
import com.zorth.aiplatform.core.exception.AiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

@WebMvcTest(AiChatController.class)
class AiChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiChatService aiChatService;

    @Test
    void returnsUnwrappedChatResponse() throws Exception {
        when(aiChatService.chat(any())).thenReturn(new ChatResponse("你好，有什么可以帮助你？"));

        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").value("你好，有什么可以帮助你？"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void echoesConversationId() throws Exception {
        when(aiChatService.chat(any())).thenReturn(new ChatResponse("继续。", "conv-1"));

        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"上一句说了什么？\",\"conversationId\":\"conv-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("继续。"))
                .andExpect(jsonPath("$.conversationId").value("conv-1"));

        verify(aiChatService).chat(argThat(request ->
                "上一句说了什么？".equals(request.message())
                        && "conv-1".equals(request.conversationId())));
    }

    @Test
    void streamsNamedSseEvents() throws Exception {
        when(aiChatService.stream(any())).thenReturn(Flux.just(
                ChatStreamEvent.start("conv-1"),
                ChatStreamEvent.delta("Hi"),
                ChatStreamEvent.completed("conv-1")));

        MvcResult result = mockMvc.perform(post("/api/v1/ai/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"message\":\"你好\",\"conversationId\":\"conv-1\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:start")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"conversationId\":\"conv-1\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:delta")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"content\":\"Hi\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:completed")));
    }

    @Test
    void rejectsNullMessageWithoutCallingService() throws Exception {
        assertInvalidRequest("/api/v1/ai/chat", "{\"message\":null}");
    }

    @Test
    void rejectsBlankMessageWithoutCallingService() throws Exception {
        assertInvalidRequest("/api/v1/ai/chat", "{\"message\":\"   \"}");
    }

    @Test
    void rejectsOversizedMessageWithoutCallingService() throws Exception {
        assertInvalidRequest("/api/v1/ai/chat", "{\"message\":\"" + "a".repeat(10_001) + "\"}");
    }

    @Test
    void rejectsOversizedConversationIdWithoutCallingService() throws Exception {
        assertInvalidRequest(
                "/api/v1/ai/chat",
                "{\"message\":\"hello\",\"conversationId\":\"" + "c".repeat(129) + "\"}");
    }

    @Test
    void rejectsInvalidStreamRequestWithoutCallingService() throws Exception {
        assertInvalidRequest("/api/v1/ai/chat/stream", "{\"message\":\"   \"}");
    }

    @Test
    void sanitizesAiServiceFailure() throws Exception {
        when(aiChatService.chat(any())).thenThrow(new AiException("secret provider detail"));

        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("AI_SERVICE_ERROR"))
                .andExpect(jsonPath("$.message").value("The AI service is temporarily unavailable"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret provider detail"))));
    }

    @Test
    void sanitizesUnexpectedFailure() throws Exception {
        when(aiChatService.chat(any())).thenThrow(new IllegalStateException("internal detail"));

        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("internal detail"))));
    }

    private void assertInvalidRequest(String path, String body) throws Exception {
        mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("The request is invalid"));

        verifyNoInteractions(aiChatService);
    }
}
