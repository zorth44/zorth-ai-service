package com.zorth.aiplatform.agent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zorth.aiplatform.datasource.model.DatabaseToolFailure;
import com.zorth.aiplatform.datasource.model.QueryResult;
import com.zorth.aiplatform.datasource.model.SqlCheckResult;
import com.zorth.aiplatform.datasource.model.TableList;
import com.zorth.aiplatform.datasource.model.TableSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

class DatabaseToolsTest {

    private H2DatabaseToolFixture fixture;
    private DatabaseTools tools;
    private ToolContext context;

    @BeforeEach
    void setUp() {
        fixture = new H2DatabaseToolFixture();
        tools = fixture.tools();
        context = fixture.context();
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    @Test
    void listTablesReturnsConfiguredTables() {
        TableList tables = (TableList) tools.listTables(context);
        assertTrue(tables.tables().stream().anyMatch(name -> name.equalsIgnoreCase("users")));
        assertTrue(tables.tables().stream().anyMatch(name -> name.equalsIgnoreCase("orders")));
        assertFalse(tables.truncated());
    }

    @Test
    void getTableSchemaReturnsColumns() {
        @SuppressWarnings("unchecked")
        List<TableSchema> schemas = (List<TableSchema>) tools.getTableSchema("orders", context);
        assertEquals(1, schemas.size());
        assertTrue(schemas.get(0).columns().stream()
                .anyMatch(column -> column.name().equalsIgnoreCase("amount")));
    }

    @Test
    void checkSqlAcceptsSelectAndRejectsDelete() {
        SqlCheckResult accepted = (SqlCheckResult) tools.checkSql("SELECT id FROM users", context);
        assertTrue(accepted.valid());

        SqlCheckResult rejected = (SqlCheckResult) tools.checkSql("DELETE FROM users", context);
        assertFalse(rejected.valid());
    }

    @Test
    void executeQueryReturnsRows() {
        QueryResult result = (QueryResult) tools.executeQuery(
                "SELECT amount FROM orders WHERE id = 1", context);
        assertEquals(1, result.rowCount());
        assertFalse(result.truncated());
        assertEquals(10000.00, ((Number) result.rows().get(0).get("amount")).doubleValue(), 0.001);
    }

    @Test
    void executeQueryBlocksWritesWithoutCheckSql() {
        DatabaseToolFailure failure = (DatabaseToolFailure) tools.executeQuery(
                "DELETE FROM users", context);
        assertFalse(failure.success());
        assertEquals("SQL_VALIDATION_ERROR", failure.errorType());
        assertFalse(failure.message().contains("Exception"));
    }

    @Test
    void executeQueryReturnsStructuredUnknownColumnError() {
        DatabaseToolFailure failure = (DatabaseToolFailure) tools.executeQuery(
                "SELECT order_time FROM orders", context);
        assertEquals("SQL_EXECUTION_ERROR", failure.errorType());
        assertFalse(failure.message().contains("at com."));
    }

    @Test
    void missingDatasourceContextIsStructured() {
        Object result = tools.listTables(new ToolContext(Map.of()));
        DatabaseToolFailure failure = assertInstanceOf(DatabaseToolFailure.class, result);
        assertEquals("MISSING_DATASOURCE", failure.errorType());
    }

    @Test
    void unknownTableIsStructured() {
        DatabaseToolFailure failure = (DatabaseToolFailure) tools.getTableSchema("missing", context);
        assertEquals("TABLE_NOT_FOUND", failure.errorType());
    }

    @Test
    void getTableSchemaRejectsMoreThanFiveTables() {
        DatabaseToolFailure failure = (DatabaseToolFailure) tools.getTableSchema(
                "a,b,c,d,e,f", context);
        assertEquals("INVALID_ARGUMENT", failure.errorType());
    }

    @Test
    void truncatesLargeResultSets() {
        try (H2DatabaseToolFixture limited = new H2DatabaseToolFixture(1, 1_048_576)) {
            QueryResult result = (QueryResult) limited.tools().executeQuery(
                    "SELECT id FROM orders", limited.context());
            assertEquals(1, result.rowCount());
            assertTrue(result.truncated());
        }
    }
}
