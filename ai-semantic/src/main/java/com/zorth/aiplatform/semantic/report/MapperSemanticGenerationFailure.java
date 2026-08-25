package com.zorth.aiplatform.semantic.report;

public record MapperSemanticGenerationFailure(
        String sourceFile, MapperSemanticFailureType type, String message) {
}
