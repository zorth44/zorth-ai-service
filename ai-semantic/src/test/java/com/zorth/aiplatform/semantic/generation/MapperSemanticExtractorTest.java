package com.zorth.aiplatform.semantic.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zorth.aiplatform.semantic.ai.MapperSemanticAiClient;
import com.zorth.aiplatform.semantic.exception.MapperSemanticExtractionException;
import com.zorth.aiplatform.semantic.model.MapperSemantic;
import com.zorth.aiplatform.semantic.prompt.MapperSemanticPromptBuilder;
import com.zorth.aiplatform.semantic.report.MapperSemanticFailureType;
import com.zorth.aiplatform.semantic.scan.MapperSourceReader;
import com.zorth.aiplatform.semantic.scan.MapperXmlPreflight;
import com.zorth.aiplatform.semantic.scan.SourceHashes;
import com.zorth.aiplatform.semantic.support.MapperSemanticFixtures;
import com.zorth.aiplatform.semantic.validation.MapperSemanticValidator;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

@ExtendWith(MockitoExtension.class)
class MapperSemanticExtractorTest {

    @TempDir
    Path tempDir;

    @Mock
    private MapperSemanticAiClient aiClient;

    private MapperSemanticExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new MapperSemanticExtractor(
                new MapperSourceReader(),
                new MapperXmlPreflight(),
                new MapperSemanticPromptBuilder(),
                aiClient,
                new MapperSemanticValidator());
    }

    @Test
    void extractsValidResultAndAppliesTrustedProvenance() throws Exception {
        Path file = copy("mappers/ordinary-select/OrdinarySelectMapper.xml", "OrdinarySelectMapper.xml");
        byte[] bytes = Files.readAllBytes(file);
        String hash = SourceHashes.sha256LowerHex(bytes);
        MapperSemantic modelOutput = MapperSemanticFixtures.singleSelect(
                "wrong.xml",
                "wrong-hash",
                "OrdinarySelectMapper",
                "com.example.order.mapper.OrdinarySelectMapper",
                "findOne");
        when(aiClient.extract(anyString(), anyString())).thenReturn(modelOutput);

        MapperSemantic result = extractor.extract(tempDir, file);

        assertEquals(hash, result.sourceHash());
        assertEquals("OrdinarySelectMapper.xml", result.sourceFile());
        assertEquals("OrdinarySelectMapper.xml", result.statements().get(0).evidence().get(0).sourceFile());
        verify(aiClient).extract(anyString(), anyString());
    }

    @Test
    void mapsReadErrorBeforeLaterStages() throws Exception {
        Path file = tempDir.resolve("Invalid.xml");
        Files.write(file, new byte[] {(byte) 0xFF, 0x00});
        MapperSemanticExtractionException ex =
                assertThrows(MapperSemanticExtractionException.class, () -> extractor.extract(tempDir, file));
        assertEquals(MapperSemanticFailureType.READ_ERROR, ex.failureType());
        verify(aiClient, never()).extract(anyString(), anyString());
    }

    @Test
    void mapsXmlValidationErrorBeforeAi() throws Exception {
        Path file = copy("mappers/malformed/BrokenMapper.xml", "BrokenMapper.xml");
        MapperSemanticExtractionException ex =
                assertThrows(MapperSemanticExtractionException.class, () -> extractor.extract(tempDir, file));
        assertEquals(MapperSemanticFailureType.XML_VALIDATION_ERROR, ex.failureType());
        verify(aiClient, never()).extract(anyString(), anyString());
    }

    @Test
    void mapsAiCallError() throws Exception {
        Path file = copy("mappers/ordinary-select/OrdinarySelectMapper.xml", "OrdinarySelectMapper.xml");
        when(aiClient.extract(anyString(), anyString()))
                .thenThrow(new MapperSemanticExtractionException(
                        MapperSemanticFailureType.AI_CALL_ERROR, "AI model invocation failed"));
        MapperSemanticExtractionException ex =
                assertThrows(MapperSemanticExtractionException.class, () -> extractor.extract(tempDir, file));
        assertEquals(MapperSemanticFailureType.AI_CALL_ERROR, ex.failureType());
    }

    @Test
    void mapsStructuredOutputError() throws Exception {
        Path file = copy("mappers/ordinary-select/OrdinarySelectMapper.xml", "OrdinarySelectMapper.xml");
        when(aiClient.extract(anyString(), anyString()))
                .thenThrow(new MapperSemanticExtractionException(
                        MapperSemanticFailureType.STRUCTURED_OUTPUT_ERROR, "Structured Mapper semantic output is invalid"));
        MapperSemanticExtractionException ex =
                assertThrows(MapperSemanticExtractionException.class, () -> extractor.extract(tempDir, file));
        assertEquals(MapperSemanticFailureType.STRUCTURED_OUTPUT_ERROR, ex.failureType());
    }

    @Test
    void mapsValidationErrorAfterAi() throws Exception {
        Path file = copy("mappers/ordinary-select/OrdinarySelectMapper.xml", "OrdinarySelectMapper.xml");
        when(aiClient.extract(anyString(), anyString()))
                .thenReturn(MapperSemanticFixtures.singleSelect(
                        "OrdinarySelectMapper.xml",
                        "ignored",
                        "OrdinarySelectMapper",
                        "com.example.order.mapper.OrdinarySelectMapper",
                        "hallucinated"));
        MapperSemanticExtractionException ex =
                assertThrows(MapperSemanticExtractionException.class, () -> extractor.extract(tempDir, file));
        assertEquals(MapperSemanticFailureType.VALIDATION_ERROR, ex.failureType());
        verify(aiClient).extract(anyString(), anyString());
    }

    private Path copy(String classpath, String fileName) throws Exception {
        Path file = tempDir.resolve(fileName);
        Files.write(file, new ClassPathResource(classpath).getInputStream().readAllBytes());
        return file;
    }
}
