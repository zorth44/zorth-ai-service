package com.zorth.aiplatform.datasource.websql.api;

public record WebSqlExecutionRequest(
        String executionId,
        String dataSourceId,
        String database,
        String statement,
        Integer rowLimit,
        Boolean readOnly,
        Integer timeoutSeconds,
        String source) {
}
