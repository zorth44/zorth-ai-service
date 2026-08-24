package com.zorth.aiplatform.datasource.websql.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WebSqlTableItem(String database, String name, String type, String comment) {
}
