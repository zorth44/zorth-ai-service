package com.zorth.aiplatform.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zorth.aiplatform.agent.AiAgentService;
import com.zorth.aiplatform.agent.SpringAiAgentService;
import com.zorth.aiplatform.agent.conversation.AgentConversationMemory;
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
import com.zorth.aiplatform.server.auth.AuthContextClient;
import com.zorth.aiplatform.server.auth.AuthUserResolver;
import com.zorth.aiplatform.server.conversation.AgentConversationQueryService;
import com.zorth.aiplatform.server.conversation.JdbcAgentConversationMemory;
import com.zorth.aiplatform.server.conversation.JdbcAgentConversationRepository;
import java.time.Clock;
import java.time.Duration;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        AiPlatformProperties.class,
        ChatProperties.class,
        AuthProperties.class,
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
    ChatMemoryRepository chatMemoryRepository() {
        return new InMemoryChatMemoryRepository();
    }

    @Bean
    ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository, ChatProperties chatProperties) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(chatProperties.memoryMaxMessages())
                .build();
    }

    @Bean
    AiChatService aiChatService(ChatClient chatClient, ChatMemory chatMemory) {
        return new SpringAiChatService(chatClient, chatMemory);
    }

    @Bean
    WebMvcConfigurer chatStreamTimeoutConfigurer(ChatProperties chatProperties) {
        return new WebMvcConfigurer() {
            @Override
            public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
                configurer.setDefaultTimeout(chatProperties.streamTimeout().toMillis());
            }
        };
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
    AuthContextClient authContextClient(
            RestClient.Builder restClientBuilder, AuthProperties authProperties, Clock agentClock) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(authProperties.connectTimeout());
        factory.setReadTimeout(authProperties.readTimeout());
        RestClient restClient = restClientBuilder.requestFactory(factory).build();
        return new AuthContextClient(restClient, authProperties, agentClock);
    }

    @Bean
    AuthUserResolver authUserResolver(
            AuthContextClient authContextClient, AuthProperties authProperties, Clock agentClock) {
        return new AuthUserResolver(authContextClient, authProperties, agentClock);
    }

    @Bean
    JdbcAgentConversationRepository jdbcAgentConversationRepository(
            JdbcTemplate jdbcTemplate, Clock agentClock) {
        return new JdbcAgentConversationRepository(jdbcTemplate, agentClock);
    }

    @Bean
    AgentConversationMemory agentConversationMemory(
            JdbcAgentConversationRepository jdbcAgentConversationRepository) {
        return new JdbcAgentConversationMemory(jdbcAgentConversationRepository, jsonMapper());
    }

    @Bean
    AgentConversationQueryService agentConversationQueryService(
            JdbcAgentConversationRepository jdbcAgentConversationRepository) {
        return new AgentConversationQueryService(jdbcAgentConversationRepository, jsonMapper());
    }

    @Bean
    AiAgentService aiAgentService(
            ChatClient chatClient,
            ToolCallingAdvisor toolCallingAdvisor,
            DateTools dateTools,
            CalculatorTools calculatorTools,
            SystemTools systemTools,
            DatabaseTools databaseTools,
            ToolExecutionSupport executionSupport,
            AgentConversationMemory agentConversationMemory,
            ChatProperties chatProperties) {
        return new SpringAiAgentService(
                chatClient,
                toolCallingAdvisor,
                new ClassPathResource("prompts/agent-system-prompt.txt"),
                new ClassPathResource("prompts/database-agent-system-prompt.txt"),
                dateTools,
                calculatorTools,
                systemTools,
                databaseTools,
                executionSupport,
                agentConversationMemory,
                chatProperties.memoryMaxMessages());
    }

    private static ObjectMapper jsonMapper() {
        return new ObjectMapper().findAndRegisterModules();
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
