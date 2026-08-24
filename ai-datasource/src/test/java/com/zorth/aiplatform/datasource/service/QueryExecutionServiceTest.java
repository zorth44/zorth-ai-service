package com.zorth.aiplatform.datasource.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zorth.aiplatform.datasource.H2TestDatabase;
import com.zorth.aiplatform.datasource.config.QueryLimits;
import com.zorth.aiplatform.datasource.config.ValidationLimits;
import com.zorth.aiplatform.datasource.exception.DatasourceException;
import com.zorth.aiplatform.datasource.model.QueryResult;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QueryExecutionServiceTest {

    private H2TestDatabase database;
    private QueryExecutionService service;

    @BeforeEach
    void setUp() {
        database = new H2TestDatabase();
        service = new QueryExecutionService(
                database.registry(),
                new SqlValidationService(new ValidationLimits(10_000, 12)),
                new QueryLimits(200, 10, 1_048_576));
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void executesReadOnlyQuery() {
        QueryResult result = service.execute(
                H2TestDatabase.DATASOURCE_ID,
                "SELECT id, amount FROM orders ORDER BY id");
        assertEquals(3, result.rowCount());
        assertFalse(result.truncated());
        assertTrue(result.columns().stream().anyMatch(column -> column.equalsIgnoreCase("amount")));
        assertEquals(3, result.rows().size());
    }

    @Test
    void truncatesWhenMaxRowsExceeded() {
        QueryExecutionService limited = new QueryExecutionService(
                database.registry(),
                new SqlValidationService(new ValidationLimits(10_000, 12)),
                new QueryLimits(2, 10, 1_048_576));
        QueryResult result = limited.execute(
                H2TestDatabase.DATASOURCE_ID,
                "SELECT id FROM orders ORDER BY id");
        assertEquals(2, result.rowCount());
        assertTrue(result.truncated());
        assertTrue(result.message().contains("2"));
    }

    @Test
    void truncatesWhenResultSizeExceeded() {
        QueryExecutionService limited = new QueryExecutionService(
                database.registry(),
                new SqlValidationService(new ValidationLimits(10_000, 12)),
                new QueryLimits(200, 10, 40));
        QueryResult result = limited.execute(
                H2TestDatabase.DATASOURCE_ID,
                "SELECT id, amount FROM orders");
        assertTrue(result.truncated());
        assertTrue(result.message().toLowerCase().contains("size"));
    }

    @Test
    void blocksDirectDelete() throws Exception {
        DatasourceException exception = assertThrows(DatasourceException.class,
                () -> service.execute(H2TestDatabase.DATASOURCE_ID, "DELETE FROM users"));
        assertEquals("SQL_VALIDATION_ERROR", exception.errorType());

        try (Connection connection = database.registry()
                .getDataSource(H2TestDatabase.DATASOURCE_ID)
                .getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM users")) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
        }
    }

    @Test
    void returnsStructuredUnknownColumnError() {
        DatasourceException exception = assertThrows(DatasourceException.class,
                () -> service.execute(
                        H2TestDatabase.DATASOURCE_ID,
                        "SELECT order_time FROM orders"));
        assertEquals("SQL_EXECUTION_ERROR", exception.errorType());
        assertTrue(exception.getMessage().toLowerCase().contains("order_time")
                || exception.getMessage().toLowerCase().contains("column"));
    }
}
