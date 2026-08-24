package com.zorth.aiplatform.datasource.registry;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zorth.aiplatform.datasource.config.DatasourceConnectionProperties;
import com.zorth.aiplatform.datasource.exception.DatasourceException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;

public final class DatasourceRegistry implements AutoCloseable {

    private final Map<String, DatasourceConnectionProperties> definitions;
    private final ConcurrentHashMap<String, HikariDataSource> pools = new ConcurrentHashMap<>();

    public DatasourceRegistry(Map<String, DatasourceConnectionProperties> definitions) {
        this.definitions = copyDefinitions(definitions);
    }

    public DataSource getDataSource(String datasourceId) {
        String id = requireId(datasourceId);
        DatasourceConnectionProperties properties = definitions.get(id);
        if (properties == null) {
            throw new DatasourceException(
                    "DATASOURCE_NOT_FOUND",
                    "Datasource '" + id + "' is not configured");
        }
        if (properties.getJdbcUrl() == null || properties.getJdbcUrl().isBlank()) {
            throw new DatasourceException(
                    "DATASOURCE_NOT_FOUND",
                    "Datasource '" + id + "' is missing a JDBC URL");
        }
        return pools.computeIfAbsent(id, ignored -> createPool(id, properties));
    }

    public boolean contains(String datasourceId) {
        return datasourceId != null && definitions.containsKey(datasourceId);
    }

    public String jdbcUrl(String datasourceId) {
        DatasourceConnectionProperties properties = definitions.get(requireId(datasourceId));
        return properties == null ? null : properties.getJdbcUrl();
    }

    @Override
    public void close() {
        pools.values().forEach(HikariDataSource::close);
        pools.clear();
    }

    private static HikariDataSource createPool(
            String datasourceId, DatasourceConnectionProperties properties) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.getJdbcUrl());
        config.setUsername(properties.getUsername() == null ? "" : properties.getUsername());
        config.setPassword(properties.getPassword() == null ? "" : properties.getPassword());
        if (properties.getDriverClassName() != null && !properties.getDriverClassName().isBlank()) {
            config.setDriverClassName(properties.getDriverClassName());
        }
        config.setPoolName("ai-datasource-" + datasourceId);
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(5_000);
        return new HikariDataSource(config);
    }

    private static String requireId(String datasourceId) {
        if (datasourceId == null || datasourceId.isBlank()) {
            throw new DatasourceException("MISSING_DATASOURCE", "datasourceId is required");
        }
        return datasourceId;
    }

    private static Map<String, DatasourceConnectionProperties> copyDefinitions(
            Map<String, DatasourceConnectionProperties> definitions) {
        Map<String, DatasourceConnectionProperties> copy = new LinkedHashMap<>();
        if (definitions != null) {
            definitions.forEach((id, properties) -> copy.put(
                    Objects.requireNonNull(id, "datasource id must not be null"),
                    Objects.requireNonNull(properties, "datasource properties must not be null")));
        }
        return Map.copyOf(copy);
    }
}
