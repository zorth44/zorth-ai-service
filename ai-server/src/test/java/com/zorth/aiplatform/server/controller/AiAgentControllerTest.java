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

import com.zorth.aiplatform.agent.AgentRequest;
import com.zorth.aiplatform.agent.AgentResponse;
import com.zorth.aiplatform.agent.AgentRuntimeContext;
import com.zorth.aiplatform.agent.AgentStreamEvent;
import com.zorth.aiplatform.agent.AiAgentService;
import com.zorth.aiplatform.core.exception.AiException;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

@WebMvcTest(AiAgentController.class)
class AiAgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiAgentService aiAgentService;

    @Test
    void returnsUnwrappedAgentResponse() throws Exception {
        when(aiAgentService.execute(any(), any())).thenReturn(new AgentResponse("今天是 2026-08-21。"));

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
        when(aiAgentService.execute(any(), any())).thenReturn(new AgentResponse("部分订单金额如下。", "conv-1"));

        mockMvc.perform(post("/api/v1/ai/agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer secret-token")
                        .content("""
                                {
                                  "conversationId": "conv-1",
                                  "datasourceId": "demo",
                                  "database": "orders",
                                  "userId": "user-9",
                                  "message": "查询今年每个月订单金额"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("部分订单金额如下。"))
                .andExpect(jsonPath("$.conversationId").value("conv-1"));

        verify(aiAgentService).execute(
                argThat((AgentRequest request) ->
                        "查询今年每个月订单金额".equals(request.message())
                                && "conv-1".equals(request.conversationId())
                                && "demo".equals(request.datasourceId())
                                && "orders".equals(request.database())
                                && "user-9".equals(request.userId())),
                argThat((AgentRuntimeContext runtime) ->
                        "Bearer secret-token".equals(runtime.authorization())));
    }

    @Test
    void messageOnlyAgentDoesNotRequireAuthorization() throws Exception {
        when(aiAgentService.execute(any(), any())).thenReturn(new AgentResponse("ok"));

        mockMvc.perform(post("/api/v1/ai/agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"今天是几号？\"}"))
                .andExpect(status().isOk());

        verify(aiAgentService).execute(
                argThat((AgentRequest request) ->
                        request.datasourceId() == null && "今天是几号？".equals(request.message())),
                argThat((AgentRuntimeContext runtime) -> runtime.authorization() == null));
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
    void streamsNamedSseEventsIncludingTools() throws Exception {
        when(aiAgentService.stream(any(), any())).thenReturn(Flux.just(
                AgentStreamEvent.start("conv-1"),
                AgentStreamEvent.tool("listTables", AgentStreamEvent.STATUS_STARTED),
                AgentStreamEvent.tool("listTables", AgentStreamEvent.STATUS_SUCCESS),
                AgentStreamEvent.delta("Hi"),
                AgentStreamEvent.completed("conv-1")));

        MvcResult result = mockMvc.perform(post("/api/v1/ai/agent/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .header("Authorization", "Bearer secret-token")
                        .content("""
                                {
                                  "conversationId": "conv-1",
                                  "datasourceId": "demo",
                                  "database": "orders",
                                  "message": "列出表"
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(Matchers.containsString("event:start")))
                .andExpect(content().string(Matchers.containsString("event:tool")))
                .andExpect(content().string(Matchers.containsString("\"toolName\":\"listTables\"")))
                .andExpect(content().string(Matchers.containsString("event:delta")))
                .andExpect(content().string(Matchers.containsString("\"content\":\"Hi\"")))
                .andExpect(content().string(Matchers.containsString("event:completed")));

        verify(aiAgentService).stream(
                argThat((AgentRequest request) ->
                        "列出表".equals(request.message())
                                && "demo".equals(request.datasourceId())
                                && "orders".equals(request.database())),
                argThat((AgentRuntimeContext runtime) ->
                        "Bearer secret-token".equals(runtime.authorization())));
    }

    @Test
    void rejectsInvalidStreamRequestWithoutCallingService() throws Exception {
        assertInvalidRequest("/api/v1/ai/agent/stream", "{\"message\":\"   \"}");
    }

    @Test
    void sanitizesAgentOrToolFailure() throws Exception {
        when(aiAgentService.execute(any(), any()))
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
        when(aiAgentService.execute(any(), any()))
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
        assertInvalidRequest("/api/v1/ai/agent", body);
    }

    private void assertInvalidRequest(String path, String body) throws Exception {
        mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("The request is invalid"));

        verifyNoInteractions(aiAgentService);
    }
}
