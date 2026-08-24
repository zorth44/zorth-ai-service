package com.zorth.aiplatform.datasource;

import com.zorth.aiplatform.datasource.config.DatasourceConnectionProperties;
import com.zorth.aiplatform.datasource.registry.DatasourceRegistry;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

public final class H2TestDatabase implements AutoCloseable {

    public static final String DATASOURCE_ID = "demo";

    private final DatasourceRegistry registry;

    public H2TestDatabase() {
        DatasourceConnectionProperties properties = new DatasourceConnectionProperties();
        properties.setJdbcUrl("jdbc:h2:mem:db-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        properties.setUsername("sa");
        properties.setPassword("");
        properties.setDriverClassName("org.h2.Driver");
        this.registry = new DatasourceRegistry(Map.of(DATASOURCE_ID, properties));
        initialize();
    }

    public DatasourceRegistry registry() {
        return registry;
    }

    private void initialize() {
        try (Connection connection = registry.getDataSource(DATASOURCE_ID).getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE users (
                      id BIGINT PRIMARY KEY,
                      name VARCHAR(100) NOT NULL,
                      email VARCHAR(200)
                    );
                    CREATE TABLE orders (
                      id BIGINT PRIMARY KEY,
                      user_id BIGINT NOT NULL,
                      amount DECIMAL(12, 2) NOT NULL,
                      created_at TIMESTAMP
                    );
                    CREATE VIEW user_emails AS SELECT email FROM users;
                    INSERT INTO users(id, name, email) VALUES (1, 'Alice', 'alice@example.com');
                    INSERT INTO orders(id, user_id, amount, created_at) VALUES
                      (1, 1, 10000.00, TIMESTAMP '2026-01-15 10:00:00'),
                      (2, 1, 20000.00, TIMESTAMP '2026-02-20 10:00:00'),
                      (3, 1, 5000.00, TIMESTAMP '2026-03-08 10:00:00');
                    COMMENT ON TABLE users IS 'Application users';
                    COMMENT ON COLUMN users.name IS 'Display name';
                    """);
        }
        catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize H2 test database", exception);
        }
    }

    @Override
    public void close() {
        registry.close();
    }
}
