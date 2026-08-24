package com.zorth.aiplatform.datasource.service;

import com.zorth.aiplatform.datasource.config.QueryLimits;
import com.zorth.aiplatform.datasource.exception.DatasourceException;
import com.zorth.aiplatform.datasource.model.QueryResult;
import com.zorth.aiplatform.datasource.port.DatasourceCall;
import com.zorth.aiplatform.datasource.port.QueryExecutionPort;
import com.zorth.aiplatform.datasource.registry.DatasourceRegistry;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;

public final class QueryExecutionService implements QueryExecutionPort {

    private final DatasourceRegistry registry;
    private final SqlValidationService validationService;
    private final QueryLimits limits;
    private final SqlLimitApplier limitApplier = new SqlLimitApplier();

    public QueryExecutionService(
            DatasourceRegistry registry,
            SqlValidationService validationService,
            QueryLimits limits) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.validationService = Objects.requireNonNull(validationService,
                "validationService must not be null");
        this.limits = Objects.requireNonNull(limits, "limits must not be null");
    }

    @Override
    public QueryResult execute(DatasourceCall call, String sql) {
        if (call == null || call.datasourceId() == null || call.datasourceId().isBlank()) {
            throw new DatasourceException("MISSING_DATASOURCE", "datasourceId is required");
        }
        return execute(call.datasourceId(), sql);
    }

    public QueryResult execute(String datasourceId, String sql) {
        validationService.requireValid(sql);
        DataSource dataSource = registry.getDataSource(datasourceId);
        SqlLimitApplier.AppliedSql applied = limitApplier.apply(
                sql,
                limits.maxRows() + 1,
                SqlLimitApplier.detect(registry.jdbcUrl(datasourceId)));

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(limits.queryTimeoutSeconds());
            statement.setMaxRows(limits.maxRows() + 1);
            boolean hasResultSet = statement.execute(applied.sql());
            if (!hasResultSet) {
                throw new DatasourceException(
                        "SQL_VALIDATION_ERROR",
                        "Only read-only SELECT queries are allowed");
            }
            try (ResultSet resultSet = statement.getResultSet()) {
                return read(resultSet);
            }
        }
        catch (DatasourceException exception) {
            throw exception;
        }
        catch (SQLException exception) {
            throw new DatasourceException(
                    "SQL_EXECUTION_ERROR",
                    safeMessage(exception),
                    exception);
        }
    }

    private QueryResult read(ResultSet resultSet) throws SQLException {
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();
        List<String> columns = new ArrayList<>(columnCount);
        for (int index = 1; index <= columnCount; index++) {
            String label = metaData.getColumnLabel(index);
            columns.add(label == null || label.isBlank() ? metaData.getColumnName(index) : label);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        int estimatedBytes = columns.toString().getBytes(StandardCharsets.UTF_8).length;
        boolean truncated = false;
        String message = null;

        while (resultSet.next()) {
            if (rows.size() >= limits.maxRows()) {
                truncated = true;
                message = "Result truncated to " + limits.maxRows() + " rows";
                break;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            int rowBytes = 0;
            for (int index = 1; index <= columnCount; index++) {
                Object value = convert(resultSet.getObject(index));
                row.put(columns.get(index - 1), value);
                rowBytes += estimateBytes(columns.get(index - 1), value);
            }
            if (estimatedBytes + rowBytes > limits.maxResultBytes()) {
                truncated = true;
                message = "Result truncated because it exceeded the maximum result size";
                break;
            }
            rows.add(row);
            estimatedBytes += rowBytes;
        }

        return new QueryResult(List.copyOf(columns), List.copyOf(rows), rows.size(), truncated, message);
    }

    private static Object convert(Object value) throws SQLException {
        if (value == null
                || value instanceof String
                || value instanceof Boolean
                || value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double
                || value instanceof BigDecimal) {
            return value;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof Time time) {
            return time.toLocalTime().toString();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().toString();
        }
        if (value instanceof LocalDate
                || value instanceof LocalTime
                || value instanceof LocalDateTime
                || value instanceof Instant) {
            return value.toString();
        }
        if (value instanceof byte[] bytes) {
            return "0x" + HexFormat.of().formatHex(bytes);
        }
        if (value instanceof Clob clob) {
            long length = Math.min(clob.length(), 4_096);
            return clob.getSubString(1, (int) length);
        }
        return String.valueOf(value);
    }

    private static int estimateBytes(String column, Object value) {
        int size = column.getBytes(StandardCharsets.UTF_8).length + 2;
        if (value == null) {
            return size + 4;
        }
        return size + String.valueOf(value).getBytes(StandardCharsets.UTF_8).length;
    }

    private static String safeMessage(SQLException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "SQL execution failed" : message;
    }
}
