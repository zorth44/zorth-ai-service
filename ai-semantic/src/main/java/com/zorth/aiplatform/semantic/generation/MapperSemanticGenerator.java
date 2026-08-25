package com.zorth.aiplatform.semantic.generation;

import com.zorth.aiplatform.semantic.exception.MapperSemanticExtractionException;
import com.zorth.aiplatform.semantic.exception.SemanticBatchException;
import com.zorth.aiplatform.semantic.model.MapperSemantic;
import com.zorth.aiplatform.semantic.report.MapperSemanticFailureType;
import com.zorth.aiplatform.semantic.report.MapperSemanticGenerationFailure;
import com.zorth.aiplatform.semantic.report.MapperSemanticGenerationReport;
import com.zorth.aiplatform.semantic.scan.MapperCandidate;
import com.zorth.aiplatform.semantic.scan.MapperFileScanner;
import com.zorth.aiplatform.semantic.scan.SourcePaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MapperSemanticGenerator {

    public static final String SOURCE_DIRECTORY_INVALID = "SEMANTIC_SOURCE_DIRECTORY_INVALID";
    public static final String OUTPUT_DIRECTORY_INVALID = "SEMANTIC_OUTPUT_DIRECTORY_INVALID";

    private static final Logger log = LoggerFactory.getLogger(MapperSemanticGenerator.class);

    private final MapperFileScanner scanner;
    private final MapperSemanticExtractor extractor;
    private final MapperSemanticOutputMapper outputMapper;
    private final MapperSemanticJsonPublisher publisher;

    public MapperSemanticGenerator(
            MapperFileScanner scanner,
            MapperSemanticExtractor extractor,
            MapperSemanticOutputMapper outputMapper,
            MapperSemanticJsonPublisher publisher) {
        this.scanner = Objects.requireNonNull(scanner, "scanner must not be null");
        this.extractor = Objects.requireNonNull(extractor, "extractor must not be null");
        this.outputMapper = Objects.requireNonNull(outputMapper, "outputMapper must not be null");
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
    }

    public MapperSemanticGenerationReport generate(MapperSemanticGenerationSettings settings) {
        Objects.requireNonNull(settings, "settings must not be null");
        Path sourceRoot = SourcePaths.normalizeRoot(settings.sourceDirectory());
        Path outputRoot = SourcePaths.normalizeRoot(settings.outputDirectory());
        if (!Files.isDirectory(sourceRoot) || !Files.isReadable(sourceRoot)) {
            throw new SemanticBatchException(
                    SOURCE_DIRECTORY_INVALID, "The configured source directory is unavailable");
        }
        if (Files.exists(outputRoot) && !Files.isDirectory(outputRoot)) {
            throw new SemanticBatchException(
                    OUTPUT_DIRECTORY_INVALID, "The configured output directory is unavailable");
        }
        long startedAt = System.nanoTime();
        log.info(
                "Mapper semantic generation started sourceDirectory={} outputDirectory={} overwrite={} maxFileSizeBytes={}",
                sourceRoot,
                outputRoot,
                settings.overwrite(),
                settings.maxFileSizeBytes());
        List<MapperCandidate> candidates = scanner.scan(sourceRoot);
        int success = 0;
        int skipped = 0;
        List<MapperSemanticGenerationFailure> failures = new ArrayList<>();
        for (MapperCandidate candidate : candidates) {
            long fileStartedAt = System.nanoTime();
            Path target = outputMapper.map(outputRoot, candidate.relativePath());
            if (!settings.overwrite() && Files.exists(target)) {
                skipped++;
                log.info(
                        "Mapper semantic candidate outcome sourceFile={} outcome=SKIPPED failureType= durationMs={}",
                        candidate.relativePath(),
                        elapsedMillis(fileStartedAt));
                continue;
            }
            try {
                long size = Files.size(candidate.absolutePath());
                if (size > settings.maxFileSizeBytes()) {
                    throw new MapperSemanticExtractionException(
                            MapperSemanticFailureType.FILE_TOO_LARGE,
                            "The Mapper file exceeds the configured size limit");
                }
                MapperSemantic semantic = extractor.extract(sourceRoot, candidate.absolutePath());
                publisher.publish(target, semantic);
                success++;
                log.info(
                        "Mapper semantic candidate outcome sourceFile={} outcome=SUCCESS failureType= durationMs={}",
                        candidate.relativePath(),
                        elapsedMillis(fileStartedAt));
            }
            catch (MapperSemanticExtractionException ex) {
                failures.add(failure(candidate.relativePath(), ex.failureType(), ex.getMessage()));
                log.info(
                        "Mapper semantic candidate outcome sourceFile={} outcome=FAILED failureType={} durationMs={}",
                        candidate.relativePath(),
                        ex.failureType(),
                        elapsedMillis(fileStartedAt));
                log.debug("Mapper semantic candidate failed sourceFile={}", candidate.relativePath(), ex);
            }
            catch (IOException | RuntimeException ex) {
                failures.add(failure(
                        candidate.relativePath(),
                        MapperSemanticFailureType.UNKNOWN,
                        "The Mapper file could not be processed"));
                log.info(
                        "Mapper semantic candidate outcome sourceFile={} outcome=FAILED failureType={} durationMs={}",
                        candidate.relativePath(),
                        MapperSemanticFailureType.UNKNOWN,
                        elapsedMillis(fileStartedAt));
                log.debug("Mapper semantic candidate failed sourceFile={}", candidate.relativePath(), ex);
            }
        }
        MapperSemanticGenerationReport report = new MapperSemanticGenerationReport(
                candidates.size(), success, failures.size(), skipped, failures);
        log.info(
                "Mapper semantic generation completed total={} success={} failed={} skipped={} durationMs={}",
                report.total(),
                report.success(),
                report.failed(),
                report.skipped(),
                elapsedMillis(startedAt));
        return report;
    }

    private static MapperSemanticGenerationFailure failure(
            String sourceFile, MapperSemanticFailureType type, String message) {
        return new MapperSemanticGenerationFailure(sourceFile, type, sanitize(message));
    }

    private static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "The Mapper file could not be processed";
        }
        return message;
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
