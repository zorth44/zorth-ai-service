package com.zorth.aiplatform.datasource.websql.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WebSqlTableDetail(
        String database,
        String table,
        List<WebSqlColumnItem> columns,
        WebSqlPrimaryKey primaryKey) {
}
