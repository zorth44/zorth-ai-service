package com.zorth.aiplatform.server.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "ai.auth")
public record AuthProperties(
        @DefaultValue("http://127.0.0.1:8090/internal/api/v1/auth/context") String contextUrl,
        @DefaultValue("local-sql-editor-key") String internalServiceKey,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("3s") Duration readTimeout,
        @DefaultValue("60s") Duration cacheTtl,
        @DefaultValue("10000") int cacheMaximumSize) {

    public AuthProperties {
        if (contextUrl == null || contextUrl.isBlank()) {
            throw new IllegalArgumentException("ai.auth.context-url must not be blank");
        }
        if (internalServiceKey == null || internalServiceKey.isBlank()) {
            throw new IllegalArgumentException("ai.auth.internal-service-key must not be blank");
        }
        if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()) {
            throw new IllegalArgumentException("ai.auth.connect-timeout must be positive");
        }
        if (readTimeout == null || readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalArgumentException("ai.auth.read-timeout must be positive");
        }
        if (cacheTtl == null || cacheTtl.isNegative()) {
            throw new IllegalArgumentException("ai.auth.cache-ttl must not be negative");
        }
        if (cacheTtl.getSeconds() > 60) {
            throw new IllegalArgumentException("ai.auth.cache-ttl must not exceed 60 seconds");
        }
        if (cacheMaximumSize < 1) {
            throw new IllegalArgumentException("ai.auth.cache-maximum-size must be at least 1");
        }
        contextUrl = contextUrl.trim();
        internalServiceKey = internalServiceKey.trim();
    }
}
