package com.zorth.aiplatform.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MapperSemanticIntegrationPolicyTest {

    @Test
    void providerTestIsRunnableButExcludedByDefault() throws Exception {
        String integration = Files.readString(
                Path.of("src/test/java/com/zorth/aiplatform/server/MapperSemanticProviderIntegrationTest.java"));
        assertTrue(integration.contains("@Tag(\"llm-integration\")"));
        assertTrue(integration.contains("EnabledIfEnvironmentVariable"));
        assertTrue(integration.contains("generator.generate(properties.toSettings())"));
        assertFalse(integration.contains("throw new AssertionError"));

        String pom = Files.readString(Path.of("pom.xml"));
        assertTrue(pom.contains("<excludedGroups>llm-integration</excludedGroups>"));
        assertTrue(pom.contains("<id>llm-integration</id>"));
    }
}
