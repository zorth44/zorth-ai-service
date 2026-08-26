package com.zorth.aiplatform.server.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zorth.aiplatform.agent.conversation.AgentToolSummary;
import com.zorth.aiplatform.core.exception.AiClientException;
import com.zorth.aiplatform.server.auth.AuthUserResolver;
import com.zorth.aiplatform.server.conversation.AgentConversationQueryService;
import com.zorth.aiplatform.server.conversation.ConversationDetail;
import com.zorth.aiplatform.server.conversation.ConversationListItem;
import com.zorth.aiplatform.server.conversation.ConversationMessageResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AgentConversationController.class)
class AgentConversationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthUserResolver authUserResolver;

    @MockitoBean
    private AgentConversationQueryService conversations;

    @BeforeEach
    void setUp() {
        when(authUserResolver.requireUserId("Bearer token-a")).thenReturn("user-a");
        when(authUserResolver.requireUserId("Bearer token-b")).thenReturn("user-b");
        when(authUserResolver.requireUserId(null)).thenThrow(AiClientException.unauthenticated());
    }

    @Test
    void listsOnlyTheResolvedUsersConversationsAsUnwrappedJson() throws Exception {
        Instant updated = Instant.parse("2026-08-26T08:00:00Z");
        when(conversations.list("user-a")).thenReturn(List.of(
                new ConversationListItem("conv-a1", "列出订单", "ds-1", "orders", updated),
                new ConversationListItem("conv-a2", "加上过滤", null, null, updated)));

        mockMvc.perform(get("/api/v1/ai/agent/conversations")
                        .header("Authorization", "Bearer token-a"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value("conv-a1"))
                .andExpect(jsonPath("$[0].title").value("列出订单"))
                .andExpect(jsonPath("$[0].datasourceId").value("ds-1"))
                .andExpect(jsonPath("$[0].database").value("orders"))
                .andExpect(jsonPath("$[1].id").value("conv-a2"))
                .andExpect(jsonPath("$[2]").doesNotExist())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$[0].id").value(org.hamcrest.Matchers.not("conv-b")));

        verify(conversations).list("user-a");
        verify(conversations, never()).list("user-b");
    }

    @Test
    void detailReturnsMessagesAndHidesOtherUsersConversations() throws Exception {
        Instant created = Instant.parse("2026-08-26T08:00:00Z");
        when(conversations.get("user-a", "conv-a")).thenReturn(new ConversationDetail(
                "conv-a",
                "列出订单",
                "ds-1",
                "orders",
                created,
                List.of(
                        new ConversationMessageResponse("m1", "user", "列出订单", null, created),
                        new ConversationMessageResponse(
                                "m2",
                                "assistant",
                                "如下",
                                List.of(new AgentToolSummary("listTables", "SUCCESS")),
                                created))));
        when(conversations.get("user-b", "conv-a"))
                .thenThrow(AiClientException.conversationNotFound());

        mockMvc.perform(get("/api/v1/ai/agent/conversations/conv-a")
                        .header("Authorization", "Bearer token-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("conv-a"))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content").value("列出订单"))
                .andExpect(jsonPath("$.messages[1].tools[0].name").value("listTables"))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(get("/api/v1/ai/agent/conversations/conv-a")
                        .header("Authorization", "Bearer token-b"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("The conversation was not found"));
    }

    @Test
    void deleteReturns204ForOwnerAnd404Otherwise() throws Exception {
        when(conversations.get("user-b", "missing"))
                .thenThrow(AiClientException.conversationNotFound());

        mockMvc.perform(delete("/api/v1/ai/agent/conversations/conv-a")
                        .header("Authorization", "Bearer token-a"))
                .andExpect(status().isNoContent());
        verify(conversations).delete("user-a", "conv-a");

        when(authUserResolver.requireUserId("Bearer token-b")).thenReturn("user-b");
        org.mockito.Mockito.doThrow(AiClientException.conversationNotFound())
                .when(conversations).delete("user-b", "conv-a");

        mockMvc.perform(delete("/api/v1/ai/agent/conversations/conv-a")
                        .header("Authorization", "Bearer token-b"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));
    }

    @Test
    void missingTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/ai/agent/conversations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("The request is unauthenticated"));
        verify(conversations, never()).list(any());
    }

    @Test
    void authContextDownReturns503() throws Exception {
        when(authUserResolver.requireUserId("Bearer token-a"))
                .thenThrow(AiClientException.authUnavailable());

        mockMvc.perform(get("/api/v1/ai/agent/conversations")
                        .header("Authorization", "Bearer token-a"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH_SERVICE_UNAVAILABLE"));
    }
}
