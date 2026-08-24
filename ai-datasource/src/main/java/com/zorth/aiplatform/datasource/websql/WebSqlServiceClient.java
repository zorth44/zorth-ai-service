package com.zorth.aiplatform.datasource.websql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zorth.aiplatform.datasource.exception.DatasourceException;
import com.zorth.aiplatform.datasource.port.DatasourceCall;
import com.zorth.aiplatform.datasource.websql.api.WebSqlCursorPage;
import com.zorth.aiplatform.datasource.websql.api.WebSqlError;
import com.zorth.aiplatform.datasource.websql.api.WebSqlExecutionRequest;
import com.zorth.aiplatform.datasource.websql.api.WebSqlExecutionResponse;
import com.zorth.aiplatform.datasource.websql.api.WebSqlTableDetail;
import com.zorth.aiplatform.datasource.websql.api.WebSqlTableItem;
import java.util.Optional;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

public final class WebSqlServiceClient {

    static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final int MAX_MESSAGE_LENGTH = 1_000;
    private static final ParameterizedTypeReference<WebSqlCursorPage<WebSqlTableItem>> TABLE_PAGE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public WebSqlServiceClient(RestClient restClient) {
        this(restClient, new ObjectMapper());
    }

    public WebSqlServiceClient(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public WebSqlCursorPage<WebSqlTableItem> listTables(
            DatasourceCall call, String types, int pageSize, String pageToken) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/data-sources/{id}/tables")
                            .queryParam("database", call.database())
                            .queryParam("types", types)
                            .queryParam("pageSize", pageSize)
                            .queryParamIfPresent("pageToken", Optional.ofNullable(pageToken))
                            .build(call.datasourceId()))
                    .headers(headers -> applyHeaders(headers, call))
                    .retrieve()
                    .body(TABLE_PAGE);
        }
        catch (RestClientResponseException exception) {
            throw map(exception);
        }
    }

    public WebSqlTableDetail tableDetail(DatasourceCall call, String table) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/data-sources/{id}/table-detail")
                            .queryParam("database", call.database())
                            .queryParam("table", table)
                            .build(call.datasourceId()))
                    .headers(headers -> applyHeaders(headers, call))
                    .retrieve()
                    .body(WebSqlTableDetail.class);
        }
        catch (RestClientResponseException exception) {
            throw map(exception);
        }
    }

    public WebSqlExecutionResponse execute(WebSqlExecutionRequest request, DatasourceCall call) {
        try {
            return restClient.post()
                    .uri("/api/v1/sql/executions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> applyHeaders(headers, call))
                    .body(request)
                    .retrieve()
                    .body(WebSqlExecutionResponse.class);
        }
        catch (RestClientResponseException exception) {
            throw map(exception);
        }
    }

    private static void applyHeaders(HttpHeaders headers, DatasourceCall call) {
        headers.set(HttpHeaders.AUTHORIZATION, call.authorization());
        if (call.requestId() != null && !call.requestId().isBlank()) {
            headers.set(REQUEST_ID_HEADER, call.requestId());
        }
    }

    DatasourceException map(RestClientResponseException exception) {
        WebSqlError error = readError(exception.getResponseBodyAsString());
        String code = error == null ? null : error.code();
        String message = truncate(error != null && error.message() != null
                ? error.message()
                : "Remote SQL service request failed");
        int status = exception.getStatusCode().value();
        return new DatasourceException(errorType(status, code, error), enrich(message, error));
    }

    private WebSqlError readError(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(body, WebSqlError.class);
        }
        catch (Exception ignored) {
            return null;
        }
    }

    private static String errorType(int status, String code, WebSqlError error) {
        if (status == 401 || status == 503) {
            return "AUTH_ERROR";
        }
        if (code == null) {
            return mappedByStatus(status);
        }
        return switch (code) {
            case "DATA_SOURCE_NOT_FOUND" -> "DATASOURCE_NOT_FOUND";
            case "DATABASE_NOT_FOUND" -> "DATABASE_NOT_FOUND";
            case "TABLE_NOT_FOUND" -> "TABLE_NOT_FOUND";
            case "SQL_EXECUTION_FAILED" -> "SQL_EXECUTION_ERROR";
            case "READ_ONLY_VIOLATION", "MULTI_STATEMENT_NOT_SUPPORTED" -> "SQL_VALIDATION_ERROR";
            case "SQL_EXECUTION_TIMEOUT" -> "SQL_TIMEOUT";
            case "EXECUTION_LIMIT_EXCEEDED" -> "RATE_LIMITED";
            case "VALIDATION_FAILED" -> databaseRequired(error) ? "MISSING_DATABASE" : "INVALID_ARGUMENT";
            default -> mappedByStatus(status);
        };
    }

    private static String mappedByStatus(int status) {
        if (status == 404) {
            return "DATASOURCE_NOT_FOUND";
        }
        if (status == 429) {
            return "RATE_LIMITED";
        }
        if (status == 504) {
            return "SQL_TIMEOUT";
        }
        return "SQL_EXECUTION_ERROR";
    }

    private static boolean databaseRequired(WebSqlError error) {
        if (error == null || error.details() == null) {
            return false;
        }
        JsonNode fieldErrors = error.details().get("fieldErrors");
        if (fieldErrors == null || !fieldErrors.isArray()) {
            return false;
        }
        for (JsonNode fieldError : fieldErrors) {
            if ("database".equals(text(fieldError, "field"))) {
                return true;
            }
        }
        return false;
    }

    private static String enrich(String message, WebSqlError error) {
        if (error == null || error.details() == null) {
            return message;
        }
        String sqlState = text(error.details(), "sqlState");
        String vendor = text(error.details(), "vendorErrorCode");
        if (sqlState == null && vendor == null) {
            return message;
        }
        StringBuilder builder = new StringBuilder(message);
        if (sqlState != null) {
            builder.append(" sqlState=").append(sqlState);
        }
        if (vendor != null) {
            builder.append(" vendorErrorCode=").append(vendor);
        }
        return truncate(builder.toString());
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String truncate(String message) {
        if (message == null || message.length() <= MAX_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_MESSAGE_LENGTH);
    }
}
