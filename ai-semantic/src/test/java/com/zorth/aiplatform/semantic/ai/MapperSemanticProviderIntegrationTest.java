package com.zorth.aiplatform.semantic.ai;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@Tag("llm-integration")
@EnabledIfEnvironmentVariable(named = "AI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "SEMANTIC_MAPPER_SOURCE", matches = ".+")
class MapperSemanticProviderIntegrationTest {

    @Test
    void realModelEvaluationIsExplicitlyOptIn() {
        throw new AssertionError(
                "Run the documented evaluation procedure instead of the default Maven lifecycle. "
                        + "See docs/mapper-semantic-generation.md.");
    }
}
