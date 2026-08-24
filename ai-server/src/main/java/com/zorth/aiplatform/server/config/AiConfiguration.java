package com.zorth.aiplatform.server.config;

import com.zorth.aiplatform.agent.AiAgentService;
import com.zorth.aiplatform.agent.SpringAiAgentService;
import com.zorth.aiplatform.agent.model.SystemInfo;
import com.zorth.aiplatform.agent.support.DatabaseToolAudit;
import com.zorth.aiplatform.agent.support.ToolExecutionSupport;
import com.zorth.aiplatform.agent.tool.CalculatorTools;
import com.zorth.aiplatform.agent.tool.DatabaseTools;
import com.zorth.aiplatform.agent.tool.DateTools;
import com.zorth.aiplatform.agent.tool.SystemTools;
import com.zorth.aiplatform.core.chat.AiChatService;
import com.zorth.aiplatform.core.chat.SpringAiChatService;
import com.zorth.aiplatform.datasource.registry.DatasourceRegistry;
import com.zorth.aiplatform.datasource.service.DatabaseMetadataService;
import com.zorth.aiplatform.datasource.service.QueryExecutionService;
import com.zorth.aiplatform.datasource.service.SqlValidationService;
import java.time.Clock;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        AiPlatformProperties.class,
        AiDatasourceProperties.class,
        DatabaseAgentProperties.class
})
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

    @Bean(destroyMethod = "close")
    DatasourceRegistry datasourceRegistry(AiDatasourceProperties properties) {
        return new DatasourceRegistry(properties.getDatasources());
    }

    @Bean
    DatabaseMetadataService databaseMetadataService(
            DatasourceRegistry datasourceRegistry,
            DatabaseAgentProperties properties) {
        return new DatabaseMetadataService(datasourceRegistry, properties.includeViews());
    }

    @Bean
    SqlValidationService sqlValidationService(DatabaseAgentProperties properties) {
        return new SqlValidationService(properties.validationLimits());
    }

    @Bean
    QueryExecutionService queryExecutionService(
            DatasourceRegistry datasourceRegistry,
            SqlValidationService sqlValidationService,
            DatabaseAgentProperties properties) {
        return new QueryExecutionService(
                datasourceRegistry, sqlValidationService, properties.queryLimits());
    }

    @Bean
    DatabaseToolAudit databaseToolAudit() {
        return new DatabaseToolAudit();
    }

    @Bean
    DatabaseTools databaseTools(
            DatabaseMetadataService databaseMetadataService,
            SqlValidationService sqlValidationService,
            QueryExecutionService queryExecutionService,
            DatabaseToolAudit databaseToolAudit,
            ToolExecutionSupport executionSupport) {
        return new DatabaseTools(
                databaseMetadataService,
                sqlValidationService,
                queryExecutionService,
                databaseToolAudit,
                executionSupport);
    }

    @Bean
    AiAgentService aiAgentService(
            ChatClient chatClient,
            ToolCallingAdvisor toolCallingAdvisor,
            DateTools dateTools,
            CalculatorTools calculatorTools,
            SystemTools systemTools,
            DatabaseTools databaseTools) {
        return new SpringAiAgentService(
                chatClient,
                toolCallingAdvisor,
                new ClassPathResource("prompts/agent-system-prompt.txt"),
                new ClassPathResource("prompts/database-agent-system-prompt.txt"),
                dateTools,
                calculatorTools,
                systemTools,
                databaseTools);
    }
}
