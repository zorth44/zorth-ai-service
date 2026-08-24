package com.zorth.aiplatform.datasource.model;

import java.util.List;

public record TableSchema(
        String tableName,
        String comment,
        List<ColumnSchema> columns,
        List<String> primaryKeys) {
}
