package com.zorth.aiplatform.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zorth.aiplatform.agent.AiAgentService;
import com.zorth.aiplatform.agent.model.SystemInfo;
import com.zorth.aiplatform.agent.tool.CalculatorTools;
import com.zorth.aiplatform.agent.tool.DatabaseTools;
import com.zorth.aiplatform.agent.tool.DateTools;
import com.zorth.aiplatform.agent.tool.SystemTools;
import com.zorth.aiplatform.datasource.registry.DatasourceRegistry;
import com.zorth.aiplatform.core.chat.AiChatService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.ai.model.chat=none",
        "ai.platform.application-name=context-test-app",
        "ai.platform.environment=context-test",
        "ai.platform.version=9.9.9-test"
})
@AutoConfigureMockMvc
@Import(AiPlatformApplicationTest.TestModelConfiguration.class)
class AiPlatformApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private AiChatService aiChatService;

    @Autowired
    private AiAgentService aiAgentService;

    @Autowired
    private DateTools dateTools;

    @Autowired
    private CalculatorTools calculatorTools;

    @Autowired
    private SystemTools systemTools;

    @Autowired
    private SystemInfo systemInfo;

    @Autowired
    private DatabaseTools databaseTools;

    @Autowired
    private DatasourceRegistry datasourceRegistry;

    @Test
    void contextLoadsAndHealthDoesNotCallModel() throws Exception {
        assertNotNull(aiChatService);
        assertNotNull(aiAgentService);
        assertNotNull(dateTools);
        assertNotNull(calculatorTools);
        assertNotNull(systemTools);
        assertNotNull(databaseTools);
        assertNotNull(datasourceRegistry);
        assertEquals(new SystemInfo("context-test-app", "context-test", "9.9.9-test"), systemInfo);

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        verifyNoInteractions(chatModel);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestModelConfiguration {

        @Bean
        ChatModel chatModel() {
            return mock(ChatModel.class);
        }
    }
}
