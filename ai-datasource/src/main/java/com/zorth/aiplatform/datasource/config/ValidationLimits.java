package com.zorth.aiplatform.datasource.config;

public record ValidationLimits(int maxSqlLength, int maxComplexity) {

    public ValidationLimits {
        if (maxSqlLength <= 0) {
            throw new IllegalArgumentException("maxSqlLength must be positive");
        }
        if (maxComplexity <= 0) {
            throw new IllegalArgumentException("maxComplexity must be positive");
        }
    }
}
