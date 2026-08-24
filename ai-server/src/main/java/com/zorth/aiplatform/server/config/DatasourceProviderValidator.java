package com.zorth.aiplatform.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DatasourceProviderValidator {

    private static final Logger log = LoggerFactory.getLogger(DatasourceProviderValidator.class);

    public DatasourceProviderValidator(String provider, String environment) {
        String normalized = provider == null || provider.isBlank() ? "web-sql" : provider.trim();
        if (!"jdbc".equalsIgnoreCase(normalized) && !"web-sql".equalsIgnoreCase(normalized)) {
            throw new IllegalStateException("ai.datasource.provider must be jdbc or web-sql");
        }
        if (!"jdbc".equalsIgnoreCase(normalized)) {
            return;
        }
        String env = environment == null ? "" : environment.trim();
        if (isProduction(env)) {
            throw new IllegalStateException(
                    "ai.datasource.provider=jdbc is not allowed when ai.platform.environment is "
                            + env);
        }
        if (!isLocalOrTest(env)) {
            log.warn(
                    "JDBC datasource provider is enabled in environment '{}'; "
                            + "this bypasses zorth-web-sql-service authorization and audit",
                    env);
        }
    }

    static boolean isProduction(String environment) {
        return "prod".equalsIgnoreCase(environment) || "production".equalsIgnoreCase(environment);
    }

    static boolean isLocalOrTest(String environment) {
        return "local".equalsIgnoreCase(environment) || "test".equalsIgnoreCase(environment);
    }
}
