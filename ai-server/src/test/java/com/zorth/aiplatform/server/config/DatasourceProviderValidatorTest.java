package com.zorth.aiplatform.server.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DatasourceProviderValidatorTest {

    @Test
    void allowsJdbcInLocalAndTest() {
        assertDoesNotThrow(() -> new DatasourceProviderValidator("jdbc", "local"));
        assertDoesNotThrow(() -> new DatasourceProviderValidator("jdbc", "test"));
    }

    @Test
    void refusesJdbcInProduction() {
        assertThrows(IllegalStateException.class,
                () -> new DatasourceProviderValidator("jdbc", "prod"));
        assertThrows(IllegalStateException.class,
                () -> new DatasourceProviderValidator("jdbc", "production"));
    }

    @Test
    void allowsWebSqlEverywhere() {
        assertDoesNotThrow(() -> new DatasourceProviderValidator("web-sql", "prod"));
    }
}
