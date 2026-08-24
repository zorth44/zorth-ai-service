package com.zorth.aiplatform.datasource.websql;

import com.zorth.aiplatform.datasource.config.QueryLimits;
import com.zorth.aiplatform.datasource.exception.DatasourceException;
import com.zorth.aiplatform.datasource.model.QueryResult;
import com.zorth.aiplatform.datasource.port.DatasourceCall;
import com.zorth.aiplatform.datasource.port.QueryExecutionPort;
import com.zorth.aiplatform.datasource.service.SqlValidationService;
import com.zorth.aiplatform.datasource.websql.api.WebSqlColumn;
import com.zorth.aiplatform.datasource.websql.api.WebSqlExecutionRequest;
import com.zorth.aiplatform.datasource.websql.api.WebSqlExecutionResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class WebSqlQueryAdapter implements QueryExecutionPort {

    static final String SOURCE_AI_AGENT = "AI_AGENT";

    private final WebSqlServiceClient client;
    private final WebSqlSettings settings;
    private final SqlValidationService validationService;
    private final QueryLimits limits;

    public WebSqlQueryAdapter(
            WebSqlServiceClient client,
            WebSqlSettings settings,
            SqlValidationService validationService,
            QueryLimits limits) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.validationService = Objects.requireNonNull(validationService,
                "validationService must not be null");
        this.limits = Objects.requireNonNull(limits, "limits must not be null");
    }

    @Override
    public QueryResult execute(DatasourceCall call, String sql) {
        validationService.requireValid(sql);
        WebSqlGuards.requireReady(call, settings);
        String executionId = call.executionId() == null || call.executionId().isBlank()
                ? UUID.randomUUID().toString()
                : call.executionId();
        WebSqlExecutionResponse response = client.execute(
                new WebSqlExecutionRequest(
                        executionId,
                        call.datasourceId(),
                        call.database(),
                        sql,
                        limits.maxRows(),
                        true,
                        limits.queryTimeoutSeconds(),
                        SOURCE_AI_AGENT),
                call);
        return map(response);
    }

    QueryResult map(WebSqlExecutionResponse response) {
        if (response == null || response.columns() == null) {
            throw new DatasourceException("SQL_EXECUTION_ERROR", "Remote SQL service returned no result");
        }
        List<String> columns = new ArrayList<>();
        for (WebSqlColumn column : response.columns()) {
            if (column == null) {
                continue;
            }
            String label = column.label() == null || column.label().isBlank()
                    ? column.name()
                    : column.label();
            columns.add(label == null || label.isBlank() ? "column" + (columns.size() + 1) : label);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        int estimatedBytes = columns.toString().getBytes(StandardCharsets.UTF_8).length;
        boolean truncated = Boolean.TRUE.equals(response.truncated());
        String message = null;
        List<List<Object>> remoteRows = response.rows() == null ? List.of() : response.rows();
        for (List<Object> remoteRow : remoteRows) {
            if (rows.size() >= limits.maxRows()) {
                truncated = true;
                message = "Result truncated to " + limits.maxRows() + " rows";
                break;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            int rowBytes = 0;
            for (int index = 0; index < columns.size(); index++) {
                Object value = remoteRow == null || index >= remoteRow.size()
                        ? null
                        : remoteRow.get(index);
                row.put(columns.get(index), value);
                rowBytes += estimateBytes(columns.get(index), value);
            }
            if (estimatedBytes + rowBytes > limits.maxResultBytes()) {
                truncated = true;
                message = "Result truncated because it exceeded the maximum result size";
                break;
            }
            rows.add(row);
            estimatedBytes += rowBytes;
        }
        return new QueryResult(
                List.copyOf(columns),
                List.copyOf(rows),
                rows.size(),
                truncated,
                message);
    }

    private static int estimateBytes(String column, Object value) {
        int size = column.getBytes(StandardCharsets.UTF_8).length + 2;
        if (value == null) {
            return size + 4;
        }
        return size + String.valueOf(value).getBytes(StandardCharsets.UTF_8).length;
    }
}
