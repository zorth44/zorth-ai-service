package com.zorth.aiplatform.datasource.websql.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WebSqlExecutionResponse(
        String executionId,
        String kind,
        List<WebSqlColumn> columns,
        List<List<Object>> rows,
        Long rowCount,
        Boolean truncated,
        String message) {
}
