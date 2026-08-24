package com.zorth.aiplatform.datasource.config;

public record QueryLimits(int maxRows, int queryTimeoutSeconds, int maxResultBytes) {

    public QueryLimits {
        if (maxRows <= 0) {
            throw new IllegalArgumentException("maxRows must be positive");
        }
        if (queryTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("queryTimeoutSeconds must be positive");
        }
        if (maxResultBytes <= 0) {
            throw new IllegalArgumentException("maxResultBytes must be positive");
        }
    }
}
