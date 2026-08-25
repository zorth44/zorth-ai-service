package com.zorth.aiplatform.semantic.exception;

public class SemanticBatchException extends SemanticException {

    public SemanticBatchException(String code, String safeMessage) {
        super(code, safeMessage);
    }

    public SemanticBatchException(String code, String safeMessage, Throwable cause) {
        super(code, safeMessage, cause);
    }
}
