package com.zorth.aiplatform.datasource.model;

public record ColumnSchema(
        String name,
        String dataType,
        Boolean nullable,
        String comment) {
}
