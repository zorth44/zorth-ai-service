package com.zorth.aiplatform.server.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zorth.aiplatform.agent.AgentResponse;
import com.zorth.aiplatform.agent.AiAgentService;
import com.zorth.aiplatform.core.exception.AiException;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AiAgentController.class)
class AiAgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiAgentService aiAgentService;

    @Test
    void returnsUnwrappedAgentResponse() throws Exception {
        when(aiAgentService.execute(any())).thenReturn(new AgentResponse("今天是 2026-08-21。"));

        mockMvc.perform(post("/api/v1/ai/agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"今天是几号？\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").value("今天是 2026-08-21。"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void acceptsOptionalDatabaseContextAndEchoesConversationId() throws Exception {
        when(aiAgentService.execute(any())).thenReturn(new AgentResponse("部分订单金额如下。", "conv-1"));

        mockMvc.perform(post("/api/v1/ai/agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "conv-1",
                                  "datasourceId": "demo",
                                  "userId": "user-9",
                                  "message": "查询今年每个月订单金额"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("部分订单金额如下。"))
                .andExpect(jsonPath("$.conversationId").value("conv-1"));

        verify(aiAgentService).execute(argThat(request ->
                "查询今年每个月订单金额".equals(request.message())
                        && "conv-1".equals(request.conversationId())
                        && "demo".equals(request.datasourceId())
                        && "user-9".equals(request.userId())));
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
    void sanitizesAgentOrToolFailure() throws Exception {
        when(aiAgentService.execute(any()))
                .thenThrow(new AiException("tool arguments and provider detail"));

        mockMvc.perform(post("/api/v1/ai/agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"divide by zero\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("AI_SERVICE_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("The AI service is temporarily unavailable"))
                .andExpect(content().string(Matchers.not(
                        Matchers.containsString("tool arguments and provider detail"))));
    }

    @Test
    void failureIsRequestScopedAndNextRequestCanSucceed() throws Exception {
        when(aiAgentService.execute(any()))
                .thenThrow(new AiException("first request failed"))
                .thenReturn(new AgentResponse("second request succeeded"));

        mockMvc.perform(post("/api/v1/ai/agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"first\"}"))
                .andExpect(status().isInternalServerError());

        mockMvc.perform(post("/api/v1/ai/agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"second\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("second request succeeded"));
    }

    private void assertInvalidRequest(String body) throws Exception {
        mockMvc.perform(post("/api/v1/ai/agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("The request is invalid"));

        verifyNoInteractions(aiAgentService);
    }
}
