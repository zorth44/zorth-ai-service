package com.zorth.aiplatform.semantic;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MapperSemanticOfflineTestPolicyTest {

    @Test
    void defaultTestsDoNotRequireProviderCredentialsOrNetwork() throws Exception {
        assertTrue(Files.exists(Path.of("src/test/java/com/zorth/aiplatform/semantic/ai/SpringAiMapperSemanticAiClientTest.java")));
        String pom = Files.readString(Path.of("pom.xml"));
        assertTrue(pom.contains("<excludedGroups>llm-integration</excludedGroups>"));
    }
}
