package com.zorth.aiplatform.semantic.exception;

public class SemanticException extends RuntimeException {

    private final String code;

    public SemanticException(String code, String safeMessage) {
        super(safeMessage);
        this.code = code;
    }

    public SemanticException(String code, String safeMessage, Throwable cause) {
        super(safeMessage, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
