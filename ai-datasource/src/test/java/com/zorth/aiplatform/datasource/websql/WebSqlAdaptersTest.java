package com.zorth.aiplatform.datasource.websql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zorth.aiplatform.datasource.config.QueryLimits;
import com.zorth.aiplatform.datasource.config.ValidationLimits;
import com.zorth.aiplatform.datasource.exception.DatasourceException;
import com.zorth.aiplatform.datasource.model.QueryResult;
import com.zorth.aiplatform.datasource.model.TableList;
import com.zorth.aiplatform.datasource.port.DatasourceCall;
import com.zorth.aiplatform.datasource.service.SqlValidationService;
import com.zorth.aiplatform.datasource.websql.api.WebSqlColumn;
import com.zorth.aiplatform.datasource.websql.api.WebSqlColumnItem;
import com.zorth.aiplatform.datasource.websql.api.WebSqlCursorPage;
import com.zorth.aiplatform.datasource.websql.api.WebSqlExecutionRequest;
import com.zorth.aiplatform.datasource.websql.api.WebSqlExecutionResponse;
import com.zorth.aiplatform.datasource.websql.api.WebSqlPrimaryKey;
import com.zorth.aiplatform.datasource.websql.api.WebSqlTableDetail;
import com.zorth.aiplatform.datasource.websql.api.WebSqlTableItem;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WebSqlAdaptersTest {

    private static final WebSqlSettings SETTINGS = new WebSqlSettings(
            "http://web-sql.test",
            5,
            20,
            5,
            2,
            List.of("ds-1"),
            false);

    @Mock
    private WebSqlServiceClient client;

    private SqlValidationService validation;
    private WebSqlQueryAdapter queries;
    private WebSqlMetadataAdapter metadata;

    @BeforeEach
    void setUp() {
        validation = new SqlValidationService(new ValidationLimits(10_000, 12));
        queries = new WebSqlQueryAdapter(
                client, SETTINGS, validation, new QueryLimits(200, 10, 1_048_576));
        metadata = new WebSqlMetadataAdapter(client, SETTINGS);
    }

    @Test
    void deleteIsRejectedBeforeHttp() {
        DatasourceException exception = assertThrows(DatasourceException.class,
                () -> queries.execute(allowedCall(), "DELETE FROM users"));
        assertEquals("SQL_VALIDATION_ERROR", exception.errorType());
        verify(client, never()).execute(any(), any());
    }

    @Test
    void executeSendsRequiredFlags() {
        when(client.execute(any(), any())).thenReturn(resultSet(
                List.of(new WebSqlColumn("id", "id", "BIGINT", "BIGINT")),
                List.of(List.of("1"))));

        queries.execute(allowedCall("exec-1"), "SELECT id FROM users");

        ArgumentCaptor<WebSqlExecutionRequest> captor =
                ArgumentCaptor.forClass(WebSqlExecutionRequest.class);
        verify(client).execute(captor.capture(), any());
        WebSqlExecutionRequest request = captor.getValue();
        assertEquals("exec-1", request.executionId());
        assertEquals("ds-1", request.dataSourceId());
        assertEquals("orders", request.database());
        assertEquals("SELECT id FROM users", request.statement());
        assertEquals(200, request.rowLimit());
        assertEquals(Boolean.TRUE, request.readOnly());
        assertEquals("AI_AGENT", request.source());
        assertEquals(10, request.timeoutSeconds());
    }

    @Test
    void mapsArrayRowsAndKeepsNumericStrings() {
        QueryResult result = queries.map(resultSet(
                List.of(new WebSqlColumn("amount", "amount", "DECIMAL", "DECIMAL")),
                List.of(List.of("12.50"))));
        assertEquals(1, result.rowCount());
        assertFalse(result.truncated());
        assertEquals("12.50", result.rows().get(0).get("amount"));
        assertInstanceOf(String.class, result.rows().get(0).get("amount"));
    }

    @Test
    void emptyAllowlistAndMissingContextNeverCallHttp() {
        WebSqlSettings closed = new WebSqlSettings(
                "http://web-sql.test", 5, 20, 5, 200, List.of(), false);
        WebSqlQueryAdapter closedQueries = new WebSqlQueryAdapter(
                client, closed, validation, new QueryLimits(200, 10, 1_048_576));
        WebSqlMetadataAdapter closedMetadata = new WebSqlMetadataAdapter(client, closed);

        assertEquals("DATASOURCE_NOT_ALLOWED", exceptionType(
                () -> closedQueries.execute(allowedCall(), "SELECT 1")));
        assertEquals("DATASOURCE_NOT_ALLOWED", exceptionType(
                () -> closedMetadata.listTables(allowedCall())));
        assertEquals("MISSING_DATABASE", exceptionType(
                () -> queries.execute(call("ds-1", null, "Bearer t", "req-1"), "SELECT 1")));
        assertEquals("AUTH_ERROR", exceptionType(
                () -> queries.execute(call("ds-1", "orders", null, "req-1"), "SELECT 1")));
        verify(client, never()).execute(any(), any());
        verify(client, never()).listTables(any(), anyString(), anyInt(), any());
    }

    @Test
    void listTablesStopsAtMaxAndMarksTruncated() {
        when(client.listTables(any(), eq("TABLE"), eq(2), isNull())).thenReturn(
                new WebSqlCursorPage<>(List.of(
                        new WebSqlTableItem("orders", "users", "TABLE", null),
                        new WebSqlTableItem("orders", "orders", "TABLE", null)),
                        "next"));

        TableList tables = metadata.listTables(allowedCall());
        assertEquals(List.of("users", "orders"), tables.tables());
        assertTrue(tables.truncated());
        verify(client).listTables(any(), eq("TABLE"), eq(2), isNull());
        verify(client, never()).listTables(any(), anyString(), anyInt(), eq("next"));
    }

    @Test
    void getTableSchemaMapsColumnsAndRejectsSixTables() {
        when(client.tableDetail(any(), eq("orders"))).thenReturn(new WebSqlTableDetail(
                "orders",
                "orders",
                List.of(new WebSqlColumnItem("amount", "decimal(12,2)", "DECIMAL", false, "amt", false)),
                new WebSqlPrimaryKey("pk", List.of("id"))));

        assertEquals("amount", metadata.getTableSchemas(allowedCall(), List.of("orders"))
                .get(0).columns().get(0).name());
        DatasourceException tooMany = assertThrows(DatasourceException.class,
                () -> metadata.getTableSchemas(allowedCall(), List.of("a", "b", "c", "d", "e", "f")));
        assertEquals("INVALID_ARGUMENT", tooMany.errorType());
        verify(client, never()).tableDetail(any(), eq("a"));
    }

    private static DatasourceCall allowedCall() {
        return allowedCall("exec-1");
    }

    private static DatasourceCall allowedCall(String executionId) {
        return call("ds-1", "orders", "Bearer token", "req-1").withExecutionId(executionId);
    }

    private static DatasourceCall call(
            String datasourceId, String database, String authorization, String requestId) {
        return new DatasourceCall(datasourceId, database, authorization, requestId, null);
    }

    private static WebSqlExecutionResponse resultSet(
            List<WebSqlColumn> columns, List<List<Object>> rows) {
        return new WebSqlExecutionResponse("exec-1", "RESULT_SET", columns, rows, (long) rows.size(), false, null);
    }

    private static String exceptionType(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected DatasourceException");
        }
        catch (DatasourceException exception) {
            return exception.errorType();
        }
    }
}
