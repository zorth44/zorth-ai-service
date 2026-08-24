package com.zorth.aiplatform.datasource.exception;

public class DatasourceException extends RuntimeException {

    private final String errorType;

    public DatasourceException(String errorType, String message) {
        super(message);
        this.errorType = errorType;
    }

    public DatasourceException(String errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
    }

    public String errorType() {
        return errorType;
    }
}
