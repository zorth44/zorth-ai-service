package com.zorth.aiplatform.datasource.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zorth.aiplatform.datasource.config.ValidationLimits;
import com.zorth.aiplatform.datasource.model.SqlCheckResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SqlValidationServiceTest {

    private SqlValidationService service;

    @BeforeEach
    void setUp() {
        service = new SqlValidationService(new ValidationLimits(10_000, 12));
    }

    @Test
    void acceptsPlainSelect() {
        assertTrue(service.check("SELECT id FROM users").valid());
    }

    @Test
    void acceptsWithSelect() {
        assertTrue(service.check("""
                WITH active AS (SELECT id FROM users)
                SELECT id FROM active
                """).valid());
    }

    @Test
    void rejectsEmptySql() {
        SqlCheckResult result = service.check("   ");
        assertFalse(result.valid());
        assertEquals("SQL_VALIDATION_ERROR", result.errorType());
    }

    @Test
    void rejectsDelete() {
        SqlCheckResult result = service.check("DELETE FROM users");
        assertFalse(result.valid());
        assertEquals("SQL_VALIDATION_ERROR", result.errorType());
    }

    @Test
    void rejectsDrop() {
        assertFalse(service.check("DROP TABLE orders").valid());
    }

    @Test
    void rejectsInsertUpdateTruncateAndGrant() {
        assertFalse(service.check("INSERT INTO users(id, name) VALUES (2, 'Bob')").valid());
        assertFalse(service.check("UPDATE users SET name = 'x'").valid());
        assertFalse(service.check("TRUNCATE TABLE users").valid());
        assertFalse(service.check("CREATE TABLE t(id INT)").valid());
        assertFalse(service.check("ALTER TABLE users ADD COLUMN age INT").valid());
        assertFalse(service.check("MERGE INTO users t USING users s ON (t.id = s.id) WHEN MATCHED THEN UPDATE SET name = s.name").valid());
    }

    @Test
    void rejectsMultiStatement() {
        SqlCheckResult result = service.check("SELECT 1; DELETE FROM users");
        assertFalse(result.valid());
        assertEquals("SQL_VALIDATION_ERROR", result.errorType());
        assertTrue(result.message().toLowerCase().contains("multiple"));
    }

    @Test
    void rejectsUnparsableSql() {
        SqlCheckResult result = service.check("SEL ECT FROM");
        assertFalse(result.valid());
        assertEquals("SQL_PARSE_ERROR", result.errorType());
    }

    @Test
    void rejectsOversizedSql() {
        SqlValidationService tight = new SqlValidationService(new ValidationLimits(20, 12));
        assertFalse(tight.check("SELECT id, name, email FROM users WHERE name = 'Alice'").valid());
    }

    @Test
    void rejectsOverlyComplexSql() {
        SqlValidationService tight = new SqlValidationService(new ValidationLimits(10_000, 2));
        assertFalse(tight.check("""
                SELECT *
                FROM users u
                JOIN orders o ON o.user_id = u.id
                JOIN orders o2 ON o2.user_id = u.id
                """).valid());
    }

    @Test
    void doesNotAcceptSelectByPrefixOnly() {
        assertFalse(service.check("SELECT * FROM users; DROP TABLE users").valid());
    }
}
