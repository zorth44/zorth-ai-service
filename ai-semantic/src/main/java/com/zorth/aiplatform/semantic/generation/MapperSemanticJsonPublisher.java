package com.zorth.aiplatform.semantic.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zorth.aiplatform.semantic.exception.MapperSemanticExtractionException;
import com.zorth.aiplatform.semantic.model.MapperSemantic;
import com.zorth.aiplatform.semantic.report.MapperSemanticFailureType;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

public final class MapperSemanticJsonPublisher {

    @FunctionalInterface
    public interface TargetReplacer {
        void replace(Path temporaryFile, Path target) throws IOException;
    }

    @FunctionalInterface
    public interface JsonWriter {
        byte[] write(MapperSemantic semantic) throws IOException;
    }

    private final ObjectMapper objectMapper;
    private final JsonWriter jsonWriter;
    private final TargetReplacer replacer;

    public MapperSemanticJsonPublisher(ObjectMapper objectMapper) {
        this(objectMapper, prettyWriter(objectMapper), MapperSemanticJsonPublisher::replaceTarget);
    }

    public MapperSemanticJsonPublisher(ObjectMapper objectMapper, TargetReplacer replacer) {
        this(objectMapper, prettyWriter(objectMapper), replacer);
    }

    public MapperSemanticJsonPublisher(ObjectMapper objectMapper, JsonWriter jsonWriter, TargetReplacer replacer) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.jsonWriter = Objects.requireNonNull(jsonWriter, "jsonWriter must not be null");
        this.replacer = Objects.requireNonNull(replacer, "replacer must not be null");
    }

    public void publish(Path target, MapperSemantic semantic) {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(semantic, "semantic must not be null");
        Path parent = target.getParent();
        Path temporary = null;
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path directory = parent == null ? Path.of(".") : parent;
            temporary = directory.resolve(target.getFileName() + "." + UUID.randomUUID() + ".tmp");
            byte[] json = jsonWriter.write(semantic);
            Files.write(temporary, json);
            MapperSemantic readBack = objectMapper.readValue(temporary.toFile(), MapperSemantic.class);
            if (!semantic.equals(readBack)) {
                throw writeError("Generated JSON did not round-trip to an equivalent Mapper semantic");
            }
            replacer.replace(temporary, target);
            temporary = null;
        }
        catch (MapperSemanticExtractionException ex) {
            throw ex;
        }
        catch (IOException | RuntimeException ex) {
            throw writeError("The Mapper semantic JSON could not be published", ex);
        }
        finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                }
                catch (IOException ignored) {
                    // Best-effort cleanup of an unpublished candidate.
                }
            }
        }
    }

    static void replaceTarget(Path temporaryFile, Path target) throws IOException {
        try {
            Files.move(temporaryFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporaryFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static JsonWriter prettyWriter(ObjectMapper objectMapper) {
        return semantic -> objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(semantic);
    }

    private static MapperSemanticExtractionException writeError(String message) {
        return new MapperSemanticExtractionException(MapperSemanticFailureType.WRITE_ERROR, message);
    }

    private static MapperSemanticExtractionException writeError(String message, Throwable cause) {
        return new MapperSemanticExtractionException(MapperSemanticFailureType.WRITE_ERROR, message, cause);
    }

    public static ObjectMapper defaultObjectMapper() {
        return new ObjectMapper();
    }
}
