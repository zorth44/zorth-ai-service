package com.zorth.aiplatform.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.zorth.aiplatform.agent.support.ToolContextKeys;
import com.zorth.aiplatform.agent.tool.CalculatorTools;
import com.zorth.aiplatform.agent.tool.DateTools;
import com.zorth.aiplatform.agent.tool.SystemTools;
import com.zorth.aiplatform.core.exception.AiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

class SpringAiAgentServiceTest {

    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec responseSpec;
    private ToolCallingAdvisor toolCallingAdvisor;
    private Resource systemPrompt;
    private DateTools dateTools;
    private CalculatorTools calculatorTools;
    private SystemTools systemTools;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
        responseSpec = mock(ChatClient.CallResponseSpec.class);
        toolCallingAdvisor = ToolCallingAdvisor.builder().build();
        systemPrompt = new ClassPathResource("prompts/agent-system-prompt.txt");
        dateTools = mock(DateTools.class);
        calculatorTools = mock(CalculatorTools.class);
        systemTools = mock(SystemTools.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
    }

    @Test
    void mapsFinalContentAndBuildsServerControlledToolContext() {
        when(responseSpec.content()).thenReturn("final answer");
        SpringAiAgentService service = serviceWithRequestId("request-123");

        AgentResponse response = service.execute(new AgentRequest("question"));

        assertEquals("final answer", response.content());
        verify(requestSpec).system(systemPrompt);
        verify(requestSpec).user("question");
        verify(requestSpec).tools(dateTools, calculatorTools, systemTools);
        verify(requestSpec).toolContext(argThat(context ->
                context.size() == 1
                        && "request-123".equals(context.get(ToolContextKeys.REQUEST_ID))));
        verify(requestSpec).advisors(same(toolCallingAdvisor));
    }

    @Test
    void permitsFinalAnswerWithoutExecutingATool() {
        when(responseSpec.content()).thenReturn("Redis is an in-memory data store.");

        AgentResponse response = serviceWithRequestId("no-tool-request")
                .execute(new AgentRequest("Explain Redis"));

        assertEquals("Redis is an in-memory data store.", response.content());
        verifyNoInteractions(dateTools, calculatorTools, systemTools);
    }

    @Test
    void translatesModelOrToolBoundaryFailureAndNextRequestCanSucceed() {
        when(requestSpec.call())
                .thenThrow(new IllegalStateException("internal provider or tool detail"))
                .thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("recovered response");
        SpringAiAgentService service = serviceWithRequestId("request-123");

        AiException failure = assertThrows(AiException.class,
                () -> service.execute(new AgentRequest("first request")));
        assertEquals("AI agent execution failed", failure.getMessage());

        AgentResponse recovered = service.execute(new AgentRequest("second request"));
        assertEquals("recovered response", recovered.content());
    }

    @Test
    void rejectsMissingFinalContent() {
        when(responseSpec.content()).thenReturn(null);

        assertThrows(AiException.class,
                () -> serviceWithRequestId("request-123")
                        .execute(new AgentRequest("question")));
    }

    private SpringAiAgentService serviceWithRequestId(String requestId) {
        return new SpringAiAgentService(
                chatClient,
                toolCallingAdvisor,
                systemPrompt,
                dateTools,
                calculatorTools,
                systemTools,
                () -> requestId);
    }
}
