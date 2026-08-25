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
import com.zorth.aiplatform.datasource.port.DatabaseMetadataPort;
import com.zorth.aiplatform.datasource.port.QueryExecutionPort;
import com.zorth.aiplatform.datasource.registry.DatasourceRegistry;
import com.zorth.aiplatform.datasource.service.DatabaseMetadataService;
import com.zorth.aiplatform.datasource.service.QueryExecutionService;
import com.zorth.aiplatform.datasource.service.SqlValidationService;
import com.zorth.aiplatform.datasource.websql.WebSqlMetadataAdapter;
import com.zorth.aiplatform.datasource.websql.WebSqlQueryAdapter;
import com.zorth.aiplatform.datasource.websql.WebSqlServiceClient;
import com.zorth.aiplatform.datasource.websql.WebSqlSettings;
import java.time.Clock;
import java.time.Duration;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        AiPlatformProperties.class,
        AiDatasourceProperties.class,
        DatabaseAgentProperties.class,
        DatasourceProviderProperties.class,
        MapperSemanticProperties.class
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

    @Bean
    DatasourceProviderValidator datasourceProviderValidator(
            DatasourceProviderProperties providerProperties,
            AiPlatformProperties platformProperties) {
        return new DatasourceProviderValidator(
                providerProperties.getProvider(),
                platformProperties.environment());
    }

    @Bean(destroyMethod = "close")
    DatasourceRegistry datasourceRegistry(AiDatasourceProperties properties) {
        return new DatasourceRegistry(properties.getDatasources());
    }

    @Bean
    SqlValidationService sqlValidationService(DatabaseAgentProperties properties) {
        return new SqlValidationService(properties.validationLimits());
    }

    @Bean
    WebSqlServiceClient webSqlServiceClient(DatasourceProviderProperties providerProperties) {
        DatasourceProviderProperties.WebSql webSql = providerProperties.getWebSql();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(webSql.getConnectTimeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(webSql.getReadTimeoutSeconds()));
        RestClient restClient = RestClient.builder()
                .baseUrl(webSql.getBaseUrl())
                .requestFactory(factory)
                .build();
        return new WebSqlServiceClient(restClient);
    }

    @Bean
    DatabaseMetadataPort databaseMetadataPort(
            DatasourceProviderProperties providerProperties,
            DatasourceRegistry datasourceRegistry,
            DatabaseAgentProperties databaseProperties,
            WebSqlServiceClient webSqlServiceClient) {
        if (providerProperties.jdbc()) {
            return new DatabaseMetadataService(datasourceRegistry, databaseProperties.includeViews());
        }
        return new WebSqlMetadataAdapter(
                webSqlServiceClient, webSqlSettings(providerProperties, databaseProperties));
    }

    @Bean
    QueryExecutionPort queryExecutionPort(
            DatasourceProviderProperties providerProperties,
            DatasourceRegistry datasourceRegistry,
            SqlValidationService sqlValidationService,
            DatabaseAgentProperties databaseProperties,
            WebSqlServiceClient webSqlServiceClient) {
        if (providerProperties.jdbc()) {
            return new QueryExecutionService(
                    datasourceRegistry, sqlValidationService, databaseProperties.queryLimits());
        }
        return new WebSqlQueryAdapter(
                webSqlServiceClient,
                webSqlSettings(providerProperties, databaseProperties),
                sqlValidationService,
                databaseProperties.queryLimits());
    }

    @Bean
    DatabaseToolAudit databaseToolAudit() {
        return new DatabaseToolAudit();
    }

    @Bean
    DatabaseTools databaseTools(
            DatabaseMetadataPort databaseMetadataPort,
            SqlValidationService sqlValidationService,
            QueryExecutionPort queryExecutionPort,
            DatabaseToolAudit databaseToolAudit,
            ToolExecutionSupport executionSupport,
            DatasourceProviderProperties providerProperties) {
        return new DatabaseTools(
                databaseMetadataPort,
                sqlValidationService,
                queryExecutionPort,
                databaseToolAudit,
                executionSupport,
                providerProperties.getWebSql().getMaxTablesPerSchemaCall());
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

    private static WebSqlSettings webSqlSettings(
            DatasourceProviderProperties providerProperties,
            DatabaseAgentProperties databaseProperties) {
        DatasourceProviderProperties.WebSql webSql = providerProperties.getWebSql();
        return new WebSqlSettings(
                webSql.getBaseUrl(),
                webSql.getConnectTimeoutSeconds(),
                webSql.getReadTimeoutSeconds(),
                webSql.getMaxTablesPerSchemaCall(),
                webSql.getMaxListedTables(),
                webSql.getAllowedDatasourceIds(),
                databaseProperties.includeViews());
    }
}
