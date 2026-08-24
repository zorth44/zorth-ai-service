package com.zorth.aiplatform.datasource.model;

import java.util.List;
import java.util.Map;

public record QueryResult(
        List<String> columns,
        List<Map<String, Object>> rows,
        Integer rowCount,
        Boolean truncated,
        String message) {
}
