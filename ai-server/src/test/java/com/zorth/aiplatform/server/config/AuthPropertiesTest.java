package com.zorth.aiplatform.server.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AuthPropertiesTest {

    @Test
    void defaultsMatchSqlEditorContract() {
        AuthProperties properties = new AuthProperties(
                "http://127.0.0.1:8090/internal/api/v1/auth/context",
                "local-sql-editor-key",
                Duration.ofSeconds(2),
                Duration.ofSeconds(3),
                Duration.ofSeconds(60),
                10_000);
        assertEquals("http://127.0.0.1:8090/internal/api/v1/auth/context", properties.contextUrl());
        assertEquals(Duration.ofSeconds(60), properties.cacheTtl());
        assertEquals(10_000, properties.cacheMaximumSize());
    }

    @Test
    void rejectsTtlAboveSixtySeconds() {
        assertThrows(IllegalArgumentException.class, () -> new AuthProperties(
                "http://127.0.0.1:8090/internal/api/v1/auth/context",
                "key",
                Duration.ofSeconds(2),
                Duration.ofSeconds(3),
                Duration.ofSeconds(61),
                10_000));
    }
}
