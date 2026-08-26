package com.zorth.aiplatform.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.zorth.aiplatform.agent.conversation.AgentStoredMessage;
import com.zorth.aiplatform.agent.conversation.InMemoryAgentConversationMemory;
import com.zorth.aiplatform.agent.support.ToolExecutionSupport;
import com.zorth.aiplatform.agent.tool.CalculatorTools;
import com.zorth.aiplatform.agent.tool.DateTools;
import com.zorth.aiplatform.agent.tool.SystemTools;
import com.zorth.aiplatform.core.chat.ChatRequest;
import com.zorth.aiplatform.core.chat.SpringAiChatService;
import com.zorth.aiplatform.core.exception.AiClientException;
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
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ClassPathResource;

@ExtendWith(MockitoExtension.class)
class SpringAiAgentServiceMemoryTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private DateTools dateTools;

    @Mock
    private CalculatorTools calculatorTools;

    @Mock
    private SystemTools systemTools;

    private InMemoryAgentConversationMemory memory;
    private SpringAiAgentService service;
    private List<Prompt> prompts;

    @BeforeEach
    void setUp() {
        lenient().when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        memory = new InMemoryAgentConversationMemory();
        prompts = new ArrayList<>();
        AtomicInteger turn = new AtomicInteger();
        lenient().when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            prompts.add(prompt);
            return response(turn.getAndIncrement() == 0 ? "订单列表如下。" : "已加上时间过滤。");
        });
        service = serviceWithMemory();
    }

    @Test
    void followUpPromptContainsPriorUserTextNotFatMessage() {
        AgentRuntimeContext user = new AgentRuntimeContext("Bearer token", "1001");
        service.execute(new AgentRequest(
                "当前SQL:\nSELECT * FROM orders",
                null,
                "ds-1",
                "spoof",
                "orders",
                "列出订单"), user);
        service.execute(new AgentRequest(
                "当前SQL:\nSELECT * FROM orders WHERE 1=1",
                "generated-conv",
                "ds-1",
                "spoof",
                "orders",
                "加上时间过滤"), user);

        assertEquals(2, prompts.size());
        List<String> second = texts(prompts.get(1));
        assertTrue(second.contains("列出订单"));
        assertTrue(second.contains("订单列表如下。"));
        assertTrue(second.contains("加上时间过滤"));
        assertFalse(second.contains("当前SQL:\nSELECT * FROM orders"));
        assertEquals(List.of(
                new AgentStoredMessage("user", "列出订单"),
                new AgentStoredMessage("assistant", "订单列表如下。"),
                new AgentStoredMessage("user", "加上时间过滤"),
                new AgentStoredMessage("assistant", "已加上时间过滤。")),
                memory.allMessages("generated-conv"));
        assertEquals("1001", memory.owner("generated-conv"));
    }

    @Test
    void storesMessageWhenUserTextIsAbsent() {
        AgentResponse response = service.execute(
                new AgentRequest("列出订单", "conv-msg", "ds-1", "spoof", "orders"),
                new AgentRuntimeContext("Bearer token", "1001"));

        assertEquals("conv-msg", response.conversationId());
        assertEquals("列出订单", memory.allMessages("conv-msg").get(0).content());
        assertFalse(texts(prompts.get(0)).stream().anyMatch(text ->
                text.contains("current editor context")));
    }

    @Test
    void bodyUserIdDoesNotTakeOwnershipOrToolContext() {
        when(chatModel.call(any(Prompt.class))).thenReturn(response("ok"));
        AgentResponse response = service.execute(
                new AgentRequest("列出订单", "conv-own", "ds-1", "spoof", "orders"),
                new AgentRuntimeContext("Bearer token", "1001"));

        assertEquals("conv-own", response.conversationId());
        assertEquals("1001", memory.owner("conv-own"));
        assertFalse(memory.exists("spoof"));
    }

    @Test
    void chatConversationIdDoesNotLeakIntoAgent() {
        InMemoryChatMemoryRepository chatStore = new InMemoryChatMemoryRepository();
        SpringAiChatService chat = new SpringAiChatService(
                ChatClient.builder(chatModel).build(),
                MessageWindowChatMemory.builder()
                        .chatMemoryRepository(chatStore)
                        .maxMessages(20)
                        .build());
        when(chatModel.call(any(Prompt.class))).thenReturn(response("chat-secret"));
        chat.chat(new ChatRequest("secret from chat", "conv-1"));

        prompts.clear();
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            prompts.add(invocation.getArgument(0));
            return response("agent-answer");
        });
        service.execute(
                new AgentRequest("agent question", "conv-1"),
                new AgentRuntimeContext("Bearer token", "agent-user"));

        List<String> agentPrompt = texts(prompts.get(0));
        assertFalse(agentPrompt.contains("secret from chat"));
        assertFalse(agentPrompt.contains("chat-secret"));
        assertTrue(agentPrompt.contains("agent question"));
    }

    @Test
    void sharedChatClientCallDoesNotReadAgentMemory() {
        service.execute(
                new AgentRequest("列出订单", "conv-shared"),
                new AgentRuntimeContext("Bearer token", "1001"));
        prompts.clear();
        ChatClient shared = ChatClient.builder(chatModel).build();
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            prompts.add(invocation.getArgument(0));
            return response("semantic");
        });
        shared.prompt().user("extract mapper").call().content();

        List<String> semanticPrompt = texts(prompts.get(0));
        assertFalse(semanticPrompt.contains("列出订单"));
        assertFalse(semanticPrompt.contains("订单列表如下。"));
        assertTrue(semanticPrompt.contains("extract mapper"));
    }

    @Test
    void otherUsersConversationIdIsRejected() {
        service.execute(
                new AgentRequest("列出订单", "conv-a"),
                new AgentRuntimeContext("Bearer token", "user-a"));

        AiClientException exception = assertThrows(AiClientException.class, () -> service.execute(
                new AgentRequest("加上时间过滤", "conv-a"),
                new AgentRuntimeContext("Bearer token", "user-b")));
        assertEquals("CONVERSATION_NOT_FOUND", exception.code());
        assertEquals(404, exception.status());
        assertEquals(1, memory.allMessages("conv-a").stream()
                .filter(AgentStoredMessage::user)
                .count());
    }

    @Test
    void otherUsersConversationIdEmitsStreamError() {
        service.execute(
                new AgentRequest("列出订单", "conv-a"),
                new AgentRuntimeContext("Bearer token", "user-a"));

        List<AgentStreamEvent> events = service.stream(
                        new AgentRequest("加上时间过滤", "conv-a"),
                        new AgentRuntimeContext("Bearer token", "user-b"))
                .collectList()
                .block(Duration.ofSeconds(2));

        assertEquals(List.of(AgentStreamEvent.error(
                "CONVERSATION_NOT_FOUND", "The conversation was not found")), events);
        assertEquals(1, memory.allMessages("conv-a").stream()
                .filter(AgentStoredMessage::user)
                .count());
    }

    @Test
    void anonymousAgentWritesNoRowsAndStillGeneratesId() {
        AgentResponse response = service.execute(new AgentRequest("今天是几号？"));

        assertEquals("generated-conv", response.conversationId());
        assertFalse(memory.exists("generated-conv"));
        assertTrue(memory.conversationIdsFor("1001").isEmpty());
    }

    @Test
    void failedTurnIsNotStored() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("provider"));

        assertThrows(Exception.class, () -> service.execute(
                new AgentRequest("列出订单", "conv-fail"),
                new AgentRuntimeContext("Bearer token", "1001")));

        assertTrue(memory.allMessages("conv-fail").isEmpty());
    }

    private SpringAiAgentService serviceWithMemory() {
        return new SpringAiAgentService(
                ChatClient.builder(chatModel).build(),
                ToolCallingAdvisor.builder().build(),
                new ClassPathResource("prompts/agent-system-prompt.txt"),
                null,
                dateTools,
                calculatorTools,
                systemTools,
                null,
                new ToolExecutionSupport(),
                () -> "request-1",
                memory,
                20,
                () -> "generated-conv");
    }

    private static org.springframework.ai.chat.model.ChatResponse response(String content) {
        return new org.springframework.ai.chat.model.ChatResponse(
                List.of(new Generation(new AssistantMessage(content))));
    }

    private static List<String> texts(Prompt prompt) {
        return prompt.getInstructions().stream().map(Message::getText).toList();
    }
}
