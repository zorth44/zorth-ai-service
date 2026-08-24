package com.zorth.aiplatform.datasource.service;

import com.zorth.aiplatform.datasource.exception.DatasourceException;
import com.zorth.aiplatform.datasource.model.ColumnSchema;
import com.zorth.aiplatform.datasource.model.TableList;
import com.zorth.aiplatform.datasource.model.TableSchema;
import com.zorth.aiplatform.datasource.port.DatabaseMetadataPort;
import com.zorth.aiplatform.datasource.port.DatasourceCall;
import com.zorth.aiplatform.datasource.registry.DatasourceRegistry;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;

public final class DatabaseMetadataService implements DatabaseMetadataPort {

    private final DatasourceRegistry registry;
    private final boolean includeViews;

    public DatabaseMetadataService(DatasourceRegistry registry, boolean includeViews) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.includeViews = includeViews;
    }

    @Override
    public TableList listTables(DatasourceCall call) {
        return TableList.complete(listTables(requireDatasourceId(call)));
    }

    @Override
    public List<TableSchema> getTableSchemas(DatasourceCall call, List<String> tableNames) {
        return getTableSchemas(requireDatasourceId(call), tableNames);
    }

    public List<String> listTables(String datasourceId) {
        DataSource dataSource = registry.getDataSource(datasourceId);
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            String schema = connection.getSchema();
            List<String> tables = new ArrayList<>();
            try (ResultSet resultSet = metaData.getTables(
                    catalog, schema, "%", tableTypes())) {
                while (resultSet.next()) {
                    String tableName = resultSet.getString("TABLE_NAME");
                    if (tableName != null && !tableName.isBlank()) {
                        tables.add(tableName);
                    }
                }
            }
            return List.copyOf(tables);
        }
        catch (SQLException exception) {
            throw new DatasourceException(
                    "SQL_EXECUTION_ERROR",
                    safeMessage(exception, "Failed to list tables"),
                    exception);
        }
    }

    public List<TableSchema> getTableSchemas(String datasourceId, List<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            throw new DatasourceException("INVALID_ARGUMENT", "At least one table name is required");
        }

        List<String> available = listTables(datasourceId);
        Map<String, String> availableByLower = new LinkedHashMap<>();
        for (String table : available) {
            availableByLower.put(table.toLowerCase(Locale.ROOT), table);
        }

        List<String> missing = new ArrayList<>();
        List<String> resolved = new ArrayList<>();
        for (String requested : tableNames) {
            if (requested == null || requested.isBlank()) {
                continue;
            }
            String match = availableByLower.get(requested.trim().toLowerCase(Locale.ROOT));
            if (match == null) {
                missing.add(requested.trim());
            }
            else {
                resolved.add(match);
            }
        }
        if (resolved.isEmpty() && missing.isEmpty()) {
            throw new DatasourceException("INVALID_ARGUMENT", "At least one table name is required");
        }
        if (!missing.isEmpty()) {
            throw new DatasourceException(
                    "TABLE_NOT_FOUND",
                    "Unknown table(s): " + String.join(", ", missing));
        }

        List<TableSchema> schemas = new ArrayList<>();
        for (String tableName : resolved) {
            schemas.add(loadSchema(datasourceId, tableName));
        }
        return List.copyOf(schemas);
    }

    private TableSchema loadSchema(String datasourceId, String tableName) {
        DataSource dataSource = registry.getDataSource(datasourceId);
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            String schema = connection.getSchema();
            return new TableSchema(
                    tableName,
                    tableComment(metaData, catalog, schema, tableName),
                    columns(metaData, catalog, schema, tableName),
                    primaryKeys(metaData, catalog, schema, tableName));
        }
        catch (SQLException exception) {
            throw new DatasourceException(
                    "SQL_EXECUTION_ERROR",
                    safeMessage(exception, "Failed to read table schema"),
                    exception);
        }
    }

    private String tableComment(
            DatabaseMetaData metaData, String catalog, String schema, String tableName)
            throws SQLException {
        try (ResultSet resultSet = metaData.getTables(
                catalog, schema, tableName, tableTypes())) {
            if (resultSet.next()) {
                return emptyToNull(resultSet.getString("REMARKS"));
            }
        }
        return null;
    }

    private List<ColumnSchema> columns(
            DatabaseMetaData metaData, String catalog, String schema, String tableName)
            throws SQLException {
        List<ColumnSchema> columns = new ArrayList<>();
        try (ResultSet resultSet = metaData.getColumns(catalog, schema, tableName, "%")) {
            while (resultSet.next()) {
                String typeName = resultSet.getString("TYPE_NAME");
                int columnSize = resultSet.getInt("COLUMN_SIZE");
                String dataType = typeName == null
                        ? null
                        : columnSize > 0 ? typeName + "(" + columnSize + ")" : typeName;
                columns.add(new ColumnSchema(
                        resultSet.getString("COLUMN_NAME"),
                        dataType,
                        nullable(resultSet.getInt("NULLABLE")),
                        emptyToNull(resultSet.getString("REMARKS"))));
            }
        }
        return List.copyOf(columns);
    }

    private List<String> primaryKeys(
            DatabaseMetaData metaData, String catalog, String schema, String tableName)
            throws SQLException {
        List<String> keys = new ArrayList<>();
        try (ResultSet resultSet = metaData.getPrimaryKeys(catalog, schema, tableName)) {
            while (resultSet.next()) {
                keys.add(resultSet.getString("COLUMN_NAME"));
            }
        }
        return List.copyOf(keys);
    }

    private String[] tableTypes() {
        return includeViews ? new String[] {"TABLE", "VIEW"} : new String[] {"TABLE"};
    }

    private static Boolean nullable(int nullable) {
        if (nullable == DatabaseMetaData.columnNoNulls) {
            return false;
        }
        if (nullable == DatabaseMetaData.columnNullable) {
            return true;
        }
        return null;
    }

    private static String requireDatasourceId(DatasourceCall call) {
        if (call == null || call.datasourceId() == null || call.datasourceId().isBlank()) {
            throw new DatasourceException("MISSING_DATASOURCE", "datasourceId is required");
        }
        return call.datasourceId();
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String safeMessage(SQLException exception, String fallback) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? fallback : message;
    }
}
