package com.zorth.aiplatform.server.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zorth.aiplatform.core.chat.AiChatService;
import com.zorth.aiplatform.core.chat.ChatResponse;
import com.zorth.aiplatform.core.exception.AiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
    void rejectsNullMessageWithoutCallingService() throws Exception {
        assertInvalidRequest("{\"message\":null}");
    }

    @Test
    void rejectsBlankMessageWithoutCallingService() throws Exception {
        assertInvalidRequest("{\"message\":\"   \"}");
    }

    @Test
    void rejectsOversizedMessageWithoutCallingService() throws Exception {
        assertInvalidRequest("{\"message\":\"" + "a".repeat(10_001) + "\"}");
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

    private void assertInvalidRequest(String body) throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("The request is invalid"));

        verifyNoInteractions(aiChatService);
    }
}
