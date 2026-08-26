package com.zorth.aiplatform.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zorth.aiplatform.semantic.generation.MapperSemanticGenerator;
import com.zorth.aiplatform.semantic.generation.MapperSemanticJsonPublisher;
import com.zorth.aiplatform.semantic.model.MapperSemantic;
import com.zorth.aiplatform.semantic.report.MapperSemanticGenerationReport;
import com.zorth.aiplatform.server.config.MapperSemanticProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Tag("llm-integration")
@EnabledIfEnvironmentVariable(named = "AI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "SEMANTIC_MAPPER_SOURCE", matches = ".+")
@EnabledIfEnvironmentVariable(named = "SEMANTIC_MAPPER_OUTPUT", matches = ".+")
@SpringBootTest(properties = "semantic.mapper.enabled=true")
@ActiveProfiles("test")
class MapperSemanticProviderIntegrationTest {

    @DynamicPropertySource
    static void mapperDirectories(DynamicPropertyRegistry registry) {
        registry.add("semantic.mapper.source-directory", () -> System.getenv("SEMANTIC_MAPPER_SOURCE"));
        registry.add("semantic.mapper.output-directory", () -> System.getenv("SEMANTIC_MAPPER_OUTPUT"));
        registry.add("semantic.mapper.overwrite", () -> "true");
    }

    @Autowired
    private MapperSemanticGenerator generator;

    @Autowired
    private MapperSemanticProperties properties;

    private final ObjectMapper objectMapper = MapperSemanticJsonPublisher.defaultObjectMapper();

    @Test
    void realProviderGeneratesReadableSchema11Artifacts() throws IOException {
        MapperSemanticGenerationReport report = generator.generate(properties.toSettings());
        String failureSummary = report.failures().stream()
                .map(failure -> failure.sourceFile() + ":" + failure.type() + ":" + failure.message())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        System.out.printf(
                "Mapper semantic integration total=%d success=%d failed=%d skipped=%d failures=%s%n",
                report.total(), report.success(), report.failed(), report.skipped(), failureSummary);

        assertTrue(report.total() > 0, "Approved source directory must contain at least one Mapper candidate");
        assertEquals(report.total(), report.success() + report.failed() + report.skipped());
        assertTrue(
                report.success() > 0,
                () -> "At least one Mapper must produce a validated artifact; failures=" + failureSummary);

        Path outputRoot = properties.outputDirectory();
        List<Path> artifacts;
        try (var paths = Files.walk(outputRoot)) {
            artifacts = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".semantic.json"))
                    .sorted()
                    .toList();
        }
        assertFalse(artifacts.isEmpty(), "Generation report succeeded without a published artifact");
        assertEquals(report.success(), artifacts.size());

        for (Path artifact : artifacts) {
            MapperSemantic semantic = objectMapper.readValue(artifact.toFile(), MapperSemantic.class);
            assertEquals(MapperSemantic.SCHEMA_VERSION, semantic.schemaVersion());
            assertFalse(semantic.statements().isEmpty());
        }

        String relativeArtifacts = artifacts.stream()
                .map(outputRoot::relativize)
                .map(Path::toString)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        System.out.printf("Mapper semantic integration artifacts=%s%n", relativeArtifacts);
    }
}
