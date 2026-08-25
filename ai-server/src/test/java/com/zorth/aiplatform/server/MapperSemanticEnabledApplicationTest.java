package com.zorth.aiplatform.server;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zorth.aiplatform.semantic.generation.MapperSemanticGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.ai.model.chat=none",
        "semantic.mapper.enabled=true",
        "ai.platform.application-name=semantic-test-app",
        "ai.platform.environment=test",
        "ai.platform.version=0.0.1-test"
})
@AutoConfigureMockMvc
@Import(MapperSemanticEnabledApplicationTest.TestModelConfiguration.class)
@DirtiesContext
class MapperSemanticEnabledApplicationTest {

    private static final Path ROOT = createRoot();

    @DynamicPropertySource
    static void registerPaths(DynamicPropertyRegistry registry) {
        registry.add("semantic.mapper.source-directory", () -> ROOT.resolve("source").toString());
        registry.add("semantic.mapper.output-directory", () -> ROOT.resolve("output").toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MapperSemanticGenerator generator;

    @Test
    void enabledContextExposesGeneratorAndEmptyReport() throws Exception {
        assertNotNull(generator);
        mockMvc.perform(post("/api/v1/semantic/mappers/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.success").value(0))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.skipped").value(0));
    }

    private static Path createRoot() {
        try {
            Path root = Files.createTempDirectory("semantic-enabled");
            Files.createDirectories(root.resolve("source"));
            return root;
        }
        catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestModelConfiguration {
        @Bean
        ChatModel chatModel() {
            return mock(ChatModel.class);
        }
    }
}
