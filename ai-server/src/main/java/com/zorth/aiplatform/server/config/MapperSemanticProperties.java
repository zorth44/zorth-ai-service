package com.zorth.aiplatform.server.config;

import com.zorth.aiplatform.semantic.generation.MapperSemanticGenerationSettings;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "semantic.mapper")
public record MapperSemanticProperties(
        @DefaultValue("false") boolean enabled,
        Path sourceDirectory,
        Path outputDirectory,
        @DefaultValue("true") boolean overwrite,
        @DefaultValue("200KB") DataSize maxFileSize) {

    public MapperSemanticProperties {
        if (maxFileSize == null) {
            maxFileSize = DataSize.ofKilobytes(200);
        }
        if (maxFileSize.toBytes() <= 0) {
            throw new IllegalArgumentException("semantic.mapper.max-file-size must be positive");
        }
        if (enabled) {
            if (sourceDirectory == null) {
                throw new IllegalArgumentException("semantic.mapper.source-directory is required when enabled");
            }
            if (outputDirectory == null) {
                throw new IllegalArgumentException("semantic.mapper.output-directory is required when enabled");
            }
        }
        sourceDirectory = normalize(sourceDirectory);
        outputDirectory = normalize(outputDirectory);
    }

    public MapperSemanticGenerationSettings toSettings() {
        return new MapperSemanticGenerationSettings(
                sourceDirectory, outputDirectory, overwrite, maxFileSize.toBytes());
    }

    private static Path normalize(Path path) {
        return path == null ? null : path.toAbsolutePath().normalize();
    }
}
