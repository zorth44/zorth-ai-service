package com.zorth.aiplatform.semantic.generation;

import java.nio.file.Path;

public record MapperSemanticGenerationSettings(
        Path sourceDirectory, Path outputDirectory, boolean overwrite, long maxFileSizeBytes) {
}
