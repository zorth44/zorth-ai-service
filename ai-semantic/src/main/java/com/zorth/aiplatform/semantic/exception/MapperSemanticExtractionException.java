package com.zorth.aiplatform.semantic.exception;

import com.zorth.aiplatform.semantic.report.MapperSemanticFailureType;

public class MapperSemanticExtractionException extends SemanticException {

    private final MapperSemanticFailureType failureType;

    public MapperSemanticExtractionException(MapperSemanticFailureType failureType, String safeMessage) {
        super(failureType.name(), safeMessage);
        this.failureType = failureType;
    }

    public MapperSemanticExtractionException(
            MapperSemanticFailureType failureType, String safeMessage, Throwable cause) {
        super(failureType.name(), safeMessage, cause);
        this.failureType = failureType;
    }

    public MapperSemanticFailureType failureType() {
        return failureType;
    }
}
