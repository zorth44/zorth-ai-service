package com.zorth.aiplatform.datasource.websql.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WebSqlError(String requestId, String code, String message, JsonNode details) {
}
