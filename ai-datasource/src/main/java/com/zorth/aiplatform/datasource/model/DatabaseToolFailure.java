package com.zorth.aiplatform.datasource.model;

public record DatabaseToolFailure(boolean success, String errorType, String message) {

    public DatabaseToolFailure(String errorType, String message) {
        this(false, errorType, message);
    }
}
