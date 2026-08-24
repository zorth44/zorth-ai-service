package com.zorth.aiplatform.datasource.websql.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WebSqlColumnItem(
        String name,
        String typeName,
        String jdbcType,
        boolean nullable,
        String comment,
        boolean primaryKey) {
}
