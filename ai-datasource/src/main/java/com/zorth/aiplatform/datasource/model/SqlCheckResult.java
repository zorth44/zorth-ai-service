package com.zorth.aiplatform.datasource.model;

public record SqlCheckResult(boolean valid, String errorType, String message) {

    public static SqlCheckResult ok() {
        return new SqlCheckResult(true, null, "SQL is a valid read-only query");
    }

    public static SqlCheckResult rejected(String errorType, String message) {
        return new SqlCheckResult(false, errorType, message);
    }
}
