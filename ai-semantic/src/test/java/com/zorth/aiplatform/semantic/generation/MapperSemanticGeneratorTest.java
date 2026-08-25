package com.zorth.aiplatform.semantic.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zorth.aiplatform.semantic.ai.MapperSemanticAiClient;
import com.zorth.aiplatform.semantic.exception.MapperSemanticExtractionException;
import com.zorth.aiplatform.semantic.exception.SemanticBatchException;
import com.zorth.aiplatform.semantic.model.MapperSemantic;
import com.zorth.aiplatform.semantic.prompt.MapperSemanticPromptBuilder;
import com.zorth.aiplatform.semantic.report.MapperSemanticFailureType;
import com.zorth.aiplatform.semantic.report.MapperSemanticGenerationReport;
import com.zorth.aiplatform.semantic.scan.MapperFileScanner;
import com.zorth.aiplatform.semantic.scan.MapperSourceReader;
import com.zorth.aiplatform.semantic.scan.MapperXmlPreflight;
import com.zorth.aiplatform.semantic.scan.SourcePaths;
import com.zorth.aiplatform.semantic.support.MapperSemanticFixtures;
import com.zorth.aiplatform.semantic.validation.MapperSemanticValidator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

@ExtendWith(MockitoExtension.class)
class MapperSemanticGeneratorTest {

    @TempDir
    Path tempDir;

    @Mock
    private MapperSemanticAiClient aiClient;

    private Path source;
    private Path output;
    private MapperSemanticGenerator generator;

    @BeforeEach
    void setUp() throws Exception {
        source = tempDir.resolve("source");
        output = tempDir.resolve("output");
        Files.createDirectories(source);
        generator = new MapperSemanticGenerator(
                new MapperFileScanner(),
                new MapperSemanticExtractor(
                        new MapperSourceReader(),
                        new MapperXmlPreflight(),
                        new MapperSemanticPromptBuilder(),
                        aiClient,
                        new MapperSemanticValidator()),
                new MapperSemanticOutputMapper(),
                new MapperSemanticJsonPublisher(MapperSemanticJsonPublisher.defaultObjectMapper()));
    }

    @Test
    void emptyDirectoryReturnsZeroCounts() {
        MapperSemanticGenerationReport report = generator.generate(settings(true, 204_800));
        assertEquals(0, report.total());
        assertEquals(0, report.success());
        assertEquals(0, report.failed());
        assertEquals(0, report.skipped());
        assertTrue(report.failures().isEmpty());
        verify(aiClient, never()).extract(anyString(), anyString());
    }

    @Test
    void allSuccessWritesMappedJson() throws Exception {
        copy("mappers/ordinary-select/OrdinarySelectMapper.xml", "ordinary-select/OrdinarySelectMapper.xml");
        copy("mappers/left-join/LeftJoinMapper.xml", "left-join/LeftJoinMapper.xml");
        when(aiClient.extract(anyString(), anyString())).thenAnswer(invocation -> semanticFor(invocation.getArgument(1)));

        MapperSemanticGenerationReport report = generator.generate(settings(true, 204_800));
        assertEquals(2, report.total());
        assertEquals(2, report.success());
        assertEquals(0, report.failed());
        assertTrue(Files.exists(output.resolve("ordinary-select/OrdinarySelectMapper.semantic.json")));
        assertTrue(Files.exists(output.resolve("left-join/LeftJoinMapper.semantic.json")));
        verify(aiClient, times(2)).extract(anyString(), anyString());
    }

    @Test
    void mixedSuccessFailureAndSkip() throws Exception {
        copy("mappers/choose-when/ChooseWhenMapper.xml", "a-ChooseWhenMapper.xml");
        copy("mappers/ordinary-select/OrdinarySelectMapper.xml", "b-OrdinarySelectMapper.xml");
        copy("mappers/left-join/LeftJoinMapper.xml", "c-LeftJoinMapper.xml");
        Files.createDirectories(output);
        Files.writeString(
                output.resolve("a-ChooseWhenMapper.semantic.json"),
                MapperSemanticJsonPublisher.defaultObjectMapper().writeValueAsString(MapperSemanticFixtures.singleSelect(
                        "a-ChooseWhenMapper.xml",
                        "old",
                        "a-ChooseWhenMapper",
                        "com.example.order.mapper.ChooseWhenMapper",
                        "queryByStatus")));

        when(aiClient.extract(anyString(), anyString())).thenAnswer(invocation -> {
            String user = invocation.getArgument(1);
            if (user.contains("OrdinarySelectMapper")) {
                throw new MapperSemanticExtractionException(
                        MapperSemanticFailureType.AI_CALL_ERROR, "AI model invocation failed");
            }
            return semanticFor(user);
        });

        MapperSemanticGenerationReport report = generator.generate(settings(false, 204_800));
        assertEquals(3, report.total());
        assertEquals(1, report.success());
        assertEquals(1, report.failed());
        assertEquals(1, report.skipped());
        assertEquals(1, report.failures().size());
        assertEquals(MapperSemanticFailureType.AI_CALL_ERROR, report.failures().get(0).type());
        assertEquals("b-OrdinarySelectMapper.xml", report.failures().get(0).sourceFile());
        verify(aiClient, times(2)).extract(anyString(), anyString());
    }

    @Test
    void oversizedFileDoesNotCallAi() throws Exception {
        copy("mappers/ordinary-select/OrdinarySelectMapper.xml", "ok.xml");
        Path huge = source.resolve("huge.xml");
        StringBuilder xml = new StringBuilder("<mapper namespace=\"ns\"><select id=\"findOne\">");
        xml.append("x".repeat(2_000));
        xml.append("</select></mapper>");
        Files.writeString(huge, xml.toString());

        when(aiClient.extract(anyString(), anyString())).thenAnswer(invocation -> semanticFor(invocation.getArgument(1)));
        MapperSemanticGenerationReport report = generator.generate(settings(true, 1_000));
        assertEquals(2, report.total());
        assertEquals(1, report.success());
        assertEquals(1, report.failed());
        assertEquals(MapperSemanticFailureType.FILE_TOO_LARGE, report.failures().get(0).type());
        verify(aiClient, times(1)).extract(anyString(), anyString());
    }

    @Test
    void overwriteFalseDoesNotReadOrCallAi() throws Exception {
        copy("mappers/ordinary-select/OrdinarySelectMapper.xml", "OrdinarySelectMapper.xml");
        Path target = output.resolve("OrdinarySelectMapper.semantic.json");
        Files.createDirectories(output);
        Files.writeString(target, "keep-me");
        MapperSemanticGenerationReport report = generator.generate(settings(false, 204_800));
        assertEquals(1, report.skipped());
        assertEquals("keep-me", Files.readString(target));
        verify(aiClient, never()).extract(anyString(), anyString());
    }

    @Test
    void failedOverwritePreservesOutput() throws Exception {
        copy("mappers/ordinary-select/OrdinarySelectMapper.xml", "OrdinarySelectMapper.xml");
        Path target = output.resolve("OrdinarySelectMapper.semantic.json");
        Files.createDirectories(output);
        String previous = MapperSemanticJsonPublisher.defaultObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(MapperSemanticFixtures.singleSelect(
                        "OrdinarySelectMapper.xml",
                        "old-hash",
                        "OrdinarySelectMapper",
                        "com.example.order.mapper.OrdinarySelectMapper",
                        "findOne"));
        Files.writeString(target, previous);
        when(aiClient.extract(anyString(), anyString()))
                .thenThrow(new MapperSemanticExtractionException(
                        MapperSemanticFailureType.AI_CALL_ERROR, "AI model invocation failed"));

        MapperSemanticGenerationReport report = generator.generate(settings(true, 204_800));
        assertEquals(1, report.failed());
        assertEquals(previous, Files.readString(target));
    }

    @Test
    void invokesAiInSortedSourceOrder() throws Exception {
        copy("mappers/ordinary-select/OrdinarySelectMapper.xml", "b.xml");
        copy("mappers/left-join/LeftJoinMapper.xml", "a.xml");
        List<String> order = new ArrayList<>();
        when(aiClient.extract(anyString(), anyString())).thenAnswer(invocation -> {
            String user = invocation.getArgument(1);
            if (user.contains("sourceFile: a.xml")) {
                order.add("a.xml");
            }
            else {
                order.add("b.xml");
            }
            return semanticFor(user);
        });
        generator.generate(settings(true, 204_800));
        assertEquals(List.of("a.xml", "b.xml"), order);
    }

    @Test
    void sanitizesFailureMessages() throws Exception {
        copy("mappers/ordinary-select/OrdinarySelectMapper.xml", "OrdinarySelectMapper.xml");
        when(aiClient.extract(anyString(), anyString()))
                .thenThrow(new MapperSemanticExtractionException(
                        MapperSemanticFailureType.AI_CALL_ERROR, "AI model invocation failed"));
        MapperSemanticGenerationReport report = generator.generate(settings(true, 204_800));
        assertEquals("AI model invocation failed", report.failures().get(0).message());
        assertTrue(!report.failures().get(0).message().contains("Bearer"));
        assertTrue(!report.toString().contains("<mapper"));
    }

    @Test
    void missingSourceDirectoryIsBatchFailure() {
        SemanticBatchException ex = assertThrows(
                SemanticBatchException.class,
                () -> generator.generate(new MapperSemanticGenerationSettings(
                        tempDir.resolve("missing"), output, true, 204_800)));
        assertEquals(MapperSemanticGenerator.SOURCE_DIRECTORY_INVALID, ex.code());
        verify(aiClient, never()).extract(anyString(), anyString());
    }

    @Test
    void generatorSourceHasNoParallelOrOutOfScopeFeatures() throws Exception {
        String sourceCode = Files.readString(Path.of(
                "src/main/java/com/zorth/aiplatform/semantic/generation/MapperSemanticGenerator.java"));
        assertTrue(!sourceCode.contains("parallel"));
        assertTrue(!sourceCode.contains("ExecutorService"));
        assertTrue(!sourceCode.contains("CompletableFuture"));
        assertTrue(!sourceCode.contains("chunk"));
        assertTrue(!sourceCode.contains("Pattern.compile"));
        assertTrue(!sourceCode.contains("VectorStore"));
        assertTrue(!sourceCode.contains("ToolCallback"));
        assertTrue(!sourceCode.contains("JdbcTemplate"));
    }

    private MapperSemanticGenerationSettings settings(boolean overwrite, long maxBytes) {
        return new MapperSemanticGenerationSettings(source, output, overwrite, maxBytes);
    }

    private Path copy(String classpath, String relative) throws Exception {
        Path file = source.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, new ClassPathResource(classpath).getInputStream().readAllBytes());
        return file;
    }

    private static MapperSemantic semanticFor(String userPrompt) {
        String sourceFile = sourceFileFrom(userPrompt);
        String mapperName = SourcePaths.mapperName(sourceFile);
        if (sourceFile.contains("LeftJoin") || "a.xml".equals(sourceFile) || "c-LeftJoinMapper.xml".equals(sourceFile)) {
            return MapperSemanticFixtures.singleSelect(
                    sourceFile,
                    "ignored",
                    mapperName,
                    "com.example.order.mapper.LeftJoinMapper",
                    "queryUserOrders");
        }
        if (sourceFile.contains("ChooseWhen")) {
            return MapperSemanticFixtures.singleSelect(
                    sourceFile,
                    "ignored",
                    mapperName,
                    "com.example.order.mapper.ChooseWhenMapper",
                    "queryByStatus");
        }
        return MapperSemanticFixtures.singleSelect(
                sourceFile,
                "ignored",
                mapperName,
                "com.example.order.mapper.OrdinarySelectMapper",
                "findOne");
    }

    private static String sourceFileFrom(String userPrompt) {
        for (String line : userPrompt.split("\\R")) {
            if (line.startsWith("sourceFile: ")) {
                return line.substring("sourceFile: ".length()).trim();
            }
        }
        throw new IllegalArgumentException("Missing sourceFile in prompt");
    }
}
