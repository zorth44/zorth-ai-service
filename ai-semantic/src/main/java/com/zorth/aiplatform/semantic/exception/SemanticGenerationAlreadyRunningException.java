package com.zorth.aiplatform.semantic.exception;

public class SemanticGenerationAlreadyRunningException extends SemanticException {

    public static final String CODE = "SEMANTIC_GENERATION_ALREADY_RUNNING";

    public SemanticGenerationAlreadyRunningException() {
        super(CODE, "Mapper semantic generation is already running");
    }
}
