package com.zorth.aiplatform.datasource.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zorth.aiplatform.datasource.H2TestDatabase;
import com.zorth.aiplatform.datasource.exception.DatasourceException;
import com.zorth.aiplatform.datasource.model.TableSchema;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DatabaseMetadataServiceTest {

    private H2TestDatabase database;
    private DatabaseMetadataService service;

    @BeforeEach
    void setUp() {
        database = new H2TestDatabase();
        service = new DatabaseMetadataService(database.registry(), false);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void listsTablesWithoutViews() {
        List<String> tables = service.listTables(H2TestDatabase.DATASOURCE_ID);
        assertTrue(tables.stream().anyMatch(name -> name.equalsIgnoreCase("users")));
        assertTrue(tables.stream().anyMatch(name -> name.equalsIgnoreCase("orders")));
        assertFalse(tables.stream().anyMatch(name -> name.equalsIgnoreCase("user_emails")));
    }

    @Test
    void includesViewsWhenConfigured() {
        DatabaseMetadataService withViews = new DatabaseMetadataService(database.registry(), true);
        List<String> tables = withViews.listTables(H2TestDatabase.DATASOURCE_ID);
        assertTrue(tables.stream().anyMatch(name -> name.equalsIgnoreCase("user_emails")));
    }

    @Test
    void returnsStructuredSchema() {
        TableSchema schema = service.getTableSchemas(H2TestDatabase.DATASOURCE_ID, List.of("orders"))
                .get(0);
        assertTrue(schema.tableName().equalsIgnoreCase("orders"));
        assertTrue(schema.columns().stream().anyMatch(column ->
                column.name().equalsIgnoreCase("amount") && column.dataType() != null));
        assertTrue(schema.primaryKeys().stream().anyMatch(key -> key.equalsIgnoreCase("id")));
    }

    @Test
    void rejectsUnknownTable() {
        DatasourceException exception = assertThrows(DatasourceException.class,
                () -> service.getTableSchemas(H2TestDatabase.DATASOURCE_ID, List.of("missing")));
        assertEquals("TABLE_NOT_FOUND", exception.errorType());
    }

    @Test
    void rejectsUnknownDatasource() {
        DatasourceException exception = assertThrows(DatasourceException.class,
                () -> service.listTables("unknown"));
        assertEquals("DATASOURCE_NOT_FOUND", exception.errorType());
    }
}
