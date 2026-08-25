package com.zorth.aiplatform.semantic.generation;

import com.zorth.aiplatform.semantic.exception.MapperSemanticExtractionException;
import com.zorth.aiplatform.semantic.report.MapperSemanticFailureType;
import com.zorth.aiplatform.semantic.scan.SourcePaths;
import java.nio.file.Path;
import java.util.Objects;

public final class MapperSemanticOutputMapper {

    public Path map(Path outputRoot, String relativeSourcePath) {
        Objects.requireNonNull(outputRoot, "outputRoot must not be null");
        Objects.requireNonNull(relativeSourcePath, "relativeSourcePath must not be null");
        if (relativeSourcePath.isBlank() || relativeSourcePath.startsWith("/") || relativeSourcePath.contains("\\")) {
            throw new MapperSemanticExtractionException(
                    MapperSemanticFailureType.WRITE_ERROR, "The Mapper relative path is invalid");
        }
        if (!relativeSourcePath.endsWith(".xml")) {
            throw new MapperSemanticExtractionException(
                    MapperSemanticFailureType.WRITE_ERROR, "The Mapper relative path is invalid");
        }
        String relativeOutput = relativeSourcePath.substring(0, relativeSourcePath.length() - 4) + ".semantic.json";
        Path root = SourcePaths.normalizeRoot(outputRoot);
        Path target = root.resolve(relativeOutput).normalize();
        if (!target.startsWith(root) || relativeOutput.contains("..")) {
            throw new MapperSemanticExtractionException(
                    MapperSemanticFailureType.WRITE_ERROR, "The generated output path is outside the output directory");
        }
        return target;
    }
}
