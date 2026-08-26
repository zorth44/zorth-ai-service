package com.zorth.aiplatform.datasource.websql;

import java.util.List;

public record WebSqlSettings(
        String baseUrl,
        int connectTimeoutSeconds,
        int readTimeoutSeconds,
        int maxTablesPerSchemaCall,
        int maxListedTables,
        List<String> allowedDatasourceIds,
        boolean includeViews) {

    public WebSqlSettings {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:8080" : baseUrl;
        allowedDatasourceIds = allowedDatasourceIds == null
                ? List.of()
                : List.copyOf(allowedDatasourceIds);
        if (connectTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("connectTimeoutSeconds must be positive");
        }
        if (readTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("readTimeoutSeconds must be positive");
        }
        if (maxTablesPerSchemaCall <= 0) {
            throw new IllegalArgumentException("maxTablesPerSchemaCall must be positive");
        }
        if (maxListedTables <= 0) {
            throw new IllegalArgumentException("maxListedTables must be positive");
        }
    }

    public boolean allows(String datasourceId) {
        if (datasourceId == null || datasourceId.isBlank()) {
            return false;
        }
        return allowedDatasourceIds.isEmpty() || allowedDatasourceIds.contains(datasourceId);
    }
}
