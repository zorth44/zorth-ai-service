package com.zorth.aiplatform.datasource.websql;

import com.zorth.aiplatform.datasource.exception.DatasourceException;
import com.zorth.aiplatform.datasource.model.ColumnSchema;
import com.zorth.aiplatform.datasource.model.TableList;
import com.zorth.aiplatform.datasource.model.TableSchema;
import com.zorth.aiplatform.datasource.port.DatabaseMetadataPort;
import com.zorth.aiplatform.datasource.port.DatasourceCall;
import com.zorth.aiplatform.datasource.websql.api.WebSqlColumnItem;
import com.zorth.aiplatform.datasource.websql.api.WebSqlCursorPage;
import com.zorth.aiplatform.datasource.websql.api.WebSqlPrimaryKey;
import com.zorth.aiplatform.datasource.websql.api.WebSqlTableDetail;
import com.zorth.aiplatform.datasource.websql.api.WebSqlTableItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class WebSqlMetadataAdapter implements DatabaseMetadataPort {

    private static final int PAGE_SIZE = 200;

    private final WebSqlServiceClient client;
    private final WebSqlSettings settings;

    public WebSqlMetadataAdapter(WebSqlServiceClient client, WebSqlSettings settings) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
    }

    @Override
    public TableList listTables(DatasourceCall call) {
        WebSqlGuards.requireReady(call, settings);
        String types = settings.includeViews() ? "TABLE,VIEW" : "TABLE";
        List<String> names = new ArrayList<>();
        String pageToken = null;
        boolean truncated = false;
        do {
            int remaining = settings.maxListedTables() - names.size();
            if (remaining <= 0) {
                truncated = true;
                break;
            }
            int pageSize = Math.min(PAGE_SIZE, remaining);
            WebSqlCursorPage<WebSqlTableItem> page = client.listTables(call, types, pageSize, pageToken);
            if (page == null || page.items() == null || page.items().isEmpty()) {
                break;
            }
            for (WebSqlTableItem item : page.items()) {
                if (item == null || item.name() == null || item.name().isBlank()) {
                    continue;
                }
                names.add(item.name());
                if (names.size() >= settings.maxListedTables()) {
                    truncated = page.nextPageToken() != null && !page.nextPageToken().isBlank();
                    break;
                }
            }
            pageToken = names.size() >= settings.maxListedTables()
                    ? null
                    : page.nextPageToken();
            if (pageToken != null && !pageToken.isBlank()
                    && names.size() >= settings.maxListedTables()) {
                truncated = true;
            }
        }
        while (pageToken != null && !pageToken.isBlank());
        return truncated
                ? TableList.truncated(names, settings.maxListedTables())
                : TableList.complete(names);
    }

    @Override
    public List<TableSchema> getTableSchemas(DatasourceCall call, List<String> tableNames) {
        WebSqlGuards.requireReady(call, settings);
        if (tableNames == null || tableNames.isEmpty()) {
            throw new DatasourceException("INVALID_ARGUMENT", "At least one table name is required");
        }
        List<String> names = tableNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .toList();
        if (names.isEmpty()) {
            throw new DatasourceException("INVALID_ARGUMENT", "At least one table name is required");
        }
        if (names.size() > settings.maxTablesPerSchemaCall()) {
            throw new DatasourceException(
                    "INVALID_ARGUMENT",
                    "Request at most " + settings.maxTablesPerSchemaCall()
                            + " tables per getTableSchema call");
        }
        List<TableSchema> schemas = new ArrayList<>();
        for (String tableName : names) {
            schemas.add(map(client.tableDetail(call, tableName), tableName));
        }
        return List.copyOf(schemas);
    }

    private static TableSchema map(WebSqlTableDetail detail, String requestedName) {
        if (detail == null) {
            throw new DatasourceException("TABLE_NOT_FOUND", "Unknown table(s): " + requestedName);
        }
        List<ColumnSchema> columns = new ArrayList<>();
        if (detail.columns() != null) {
            for (WebSqlColumnItem column : detail.columns()) {
                if (column == null || column.name() == null) {
                    continue;
                }
                columns.add(new ColumnSchema(
                        column.name(),
                        column.typeName(),
                        column.nullable(),
                        emptyToNull(column.comment())));
            }
        }
        List<String> primaryKeys = primaryKeys(detail.primaryKey(), detail.columns());
        String tableName = detail.table() == null || detail.table().isBlank()
                ? requestedName
                : detail.table();
        return new TableSchema(tableName, null, List.copyOf(columns), primaryKeys);
    }

    private static List<String> primaryKeys(WebSqlPrimaryKey primaryKey, List<WebSqlColumnItem> columns) {
        if (primaryKey != null && primaryKey.columns() != null && !primaryKey.columns().isEmpty()) {
            return List.copyOf(primaryKey.columns());
        }
        if (columns == null) {
            return List.of();
        }
        return columns.stream()
                .filter(column -> column != null && column.primaryKey() && column.name() != null)
                .map(WebSqlColumnItem::name)
                .toList();
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
