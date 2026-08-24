package com.zorth.aiplatform.server.config;

import com.zorth.aiplatform.agent.AiAgentService;
import com.zorth.aiplatform.agent.SpringAiAgentService;
import com.zorth.aiplatform.agent.model.SystemInfo;
import com.zorth.aiplatform.agent.support.ToolExecutionSupport;
import com.zorth.aiplatform.agent.tool.CalculatorTools;
import com.zorth.aiplatform.agent.tool.DateTools;
import com.zorth.aiplatform.agent.tool.SystemTools;
import com.zorth.aiplatform.core.chat.AiChatService;
import com.zorth.aiplatform.core.chat.SpringAiChatService;
import java.time.Clock;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiPlatformProperties.class)
public class AiConfiguration {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    AiChatService aiChatService(ChatClient chatClient) {
        return new SpringAiChatService(chatClient);
    }

    @Bean
    Clock agentClock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    ToolExecutionSupport toolExecutionSupport() {
        return new ToolExecutionSupport();
    }

    @Bean
    DateTools dateTools(Clock agentClock, ToolExecutionSupport executionSupport) {
        return new DateTools(agentClock, executionSupport);
    }

    @Bean
    CalculatorTools calculatorTools(ToolExecutionSupport executionSupport) {
        return new CalculatorTools(executionSupport);
    }

    @Bean
    SystemInfo systemInfo(AiPlatformProperties properties) {
        return new SystemInfo(
                properties.applicationName(),
                properties.environment(),
                properties.version());
    }

    @Bean
    SystemTools systemTools(SystemInfo systemInfo, ToolExecutionSupport executionSupport) {
        return new SystemTools(systemInfo, executionSupport);
    }

    @Bean
    ToolCallingAdvisor toolCallingAdvisor() {
        return ToolCallingAdvisor.builder().build();
    }

    @Bean
    AiAgentService aiAgentService(
            ChatClient chatClient,
            ToolCallingAdvisor toolCallingAdvisor,
            DateTools dateTools,
            CalculatorTools calculatorTools,
            SystemTools systemTools) {
        return new SpringAiAgentService(
                chatClient,
                toolCallingAdvisor,
                new ClassPathResource("prompts/agent-system-prompt.txt"),
                dateTools,
                calculatorTools,
                systemTools);
    }
}
