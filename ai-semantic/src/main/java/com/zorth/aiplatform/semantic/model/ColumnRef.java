package com.zorth.aiplatform.semantic.model;

public record ColumnRef(String table, String column, String alias, ColumnUsage usage) {
}
