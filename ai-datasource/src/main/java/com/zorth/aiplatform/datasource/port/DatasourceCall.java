package com.zorth.aiplatform.datasource.port;

public record DatasourceCall(
        String datasourceId,
        String database,
        String authorization,
        String requestId,
        String executionId) {

    public static DatasourceCall of(String datasourceId) {
        return new DatasourceCall(datasourceId, null, null, null, null);
    }

    public DatasourceCall withExecutionId(String executionId) {
        return new DatasourceCall(datasourceId, database, authorization, requestId, executionId);
    }
}
