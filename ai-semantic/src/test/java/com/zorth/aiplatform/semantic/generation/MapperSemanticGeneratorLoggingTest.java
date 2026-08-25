package com.zorth.aiplatform.semantic.generation;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zorth.aiplatform.semantic.ai.MapperSemanticAiClient;
import com.zorth.aiplatform.semantic.exception.MapperSemanticExtractionException;
import com.zorth.aiplatform.semantic.prompt.MapperSemanticPromptBuilder;
import com.zorth.aiplatform.semantic.report.MapperSemanticFailureType;
import com.zorth.aiplatform.semantic.scan.MapperFileScanner;
import com.zorth.aiplatform.semantic.scan.MapperSourceReader;
import com.zorth.aiplatform.semantic.scan.MapperXmlPreflight;
import com.zorth.aiplatform.semantic.support.MapperSemanticFixtures;
import com.zorth.aiplatform.semantic.validation.MapperSemanticValidator;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

@ExtendWith(MockitoExtension.class)
class MapperSemanticGeneratorLoggingTest {

    @TempDir
    Path tempDir;

    @Mock
    private MapperSemanticAiClient aiClient;

    @Test
    void infoLogsCountsAndRelativePathsWithoutSecretsOrPayloads() throws Exception {
        Path source = tempDir.resolve("source");
        Path output = tempDir.resolve("output");
        Files.createDirectories(source);
        Path file = source.resolve("OrdinarySelectMapper.xml");
        Files.write(file, new ClassPathResource("mappers/ordinary-select/OrdinarySelectMapper.xml")
                .getInputStream()
                .readAllBytes());
        when(aiClient.extract(anyString(), anyString()))
                .thenThrow(new MapperSemanticExtractionException(
                        MapperSemanticFailureType.AI_CALL_ERROR, "AI model invocation failed") {
                    {
                        initCause(new IllegalStateException("Authorization: Bearer secret-token\n<mapper>xml</mapper>"));
                    }
                });

        Logger logger = (Logger) LoggerFactory.getLogger(MapperSemanticGenerator.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        Level previous = logger.getLevel();
        logger.setLevel(Level.INFO);
        try {
            new MapperSemanticGenerator(
                    new MapperFileScanner(),
                    new MapperSemanticExtractor(
                            new MapperSourceReader(),
                            new MapperXmlPreflight(),
                            new MapperSemanticPromptBuilder(),
                            aiClient,
                            new MapperSemanticValidator()),
                    new MapperSemanticOutputMapper(),
                    new MapperSemanticJsonPublisher(MapperSemanticJsonPublisher.defaultObjectMapper()))
                    .generate(new MapperSemanticGenerationSettings(source, output, true, 204_800));
        }
        finally {
            logger.detachAppender(appender);
            logger.setLevel(previous);
        }

        String info = appender.list.stream()
                .filter(event -> event.getLevel() == Level.INFO)
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
        assertTrue(info.contains("Mapper semantic generation started"));
        assertTrue(info.contains("total=1"));
        assertTrue(info.contains("failed=1"));
        assertTrue(info.contains("OrdinarySelectMapper.xml"));
        assertTrue(info.contains("AI_CALL_ERROR"));
        assertTrue(!info.contains("secret-token"));
        assertTrue(!info.contains("Bearer"));
        assertTrue(!info.contains("<mapper"));
        assertTrue(!info.contains("You are extracting"));
        assertTrue(!info.contains("schemaVersion"));
    }
}
