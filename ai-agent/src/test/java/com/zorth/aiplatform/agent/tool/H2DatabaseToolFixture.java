package com.zorth.aiplatform.agent.tool;

import com.zorth.aiplatform.agent.support.DatabaseToolAudit;
import com.zorth.aiplatform.agent.support.ToolContextKeys;
import com.zorth.aiplatform.agent.support.ToolExecutionSupport;
import com.zorth.aiplatform.datasource.config.DatasourceConnectionProperties;
import com.zorth.aiplatform.datasource.config.QueryLimits;
import com.zorth.aiplatform.datasource.config.ValidationLimits;
import com.zorth.aiplatform.datasource.registry.DatasourceRegistry;
import com.zorth.aiplatform.datasource.service.DatabaseMetadataService;
import com.zorth.aiplatform.datasource.service.QueryExecutionService;
import com.zorth.aiplatform.datasource.service.SqlValidationService;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.chat.model.ToolContext;

public final class H2DatabaseToolFixture implements AutoCloseable {

    public static final String DATASOURCE_ID = "demo";

    private final DatasourceRegistry registry;
    private final DatabaseTools tools;

    public H2DatabaseToolFixture() {
        this(200, 1_048_576);
    }

    public H2DatabaseToolFixture(int maxRows, int maxResultBytes) {
        DatasourceConnectionProperties properties = new DatasourceConnectionProperties();
        properties.setJdbcUrl("jdbc:h2:mem:agent-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        properties.setUsername("sa");
        properties.setPassword("");
        properties.setDriverClassName("org.h2.Driver");
        this.registry = new DatasourceRegistry(Map.of(DATASOURCE_ID, properties));
        initialize();
        SqlValidationService validation = new SqlValidationService(new ValidationLimits(10_000, 12));
        this.tools = new DatabaseTools(
                new DatabaseMetadataService(registry, false),
                validation,
                new QueryExecutionService(registry, validation, new QueryLimits(maxRows, 10, maxResultBytes)),
                new DatabaseToolAudit(),
                new ToolExecutionSupport());
    }

    public DatabaseTools tools() {
        return tools;
    }

    public ToolContext context() {
        return new ToolContext(Map.of(
                ToolContextKeys.REQUEST_ID, "req-1",
                ToolContextKeys.CONVERSATION_ID, "conv-1",
                ToolContextKeys.USER_ID, "user-1",
                ToolContextKeys.DATASOURCE_ID, DATASOURCE_ID));
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
                    INSERT INTO users(id, name, email) VALUES (1, 'Alice', 'alice@example.com');
                    INSERT INTO orders(id, user_id, amount, created_at) VALUES
                      (1, 1, 10000.00, TIMESTAMP '2026-01-15 10:00:00'),
                      (2, 1, 20000.00, TIMESTAMP '2026-02-20 10:00:00'),
                      (3, 1, 5000.00, TIMESTAMP '2026-03-08 10:00:00');
                    """);
        }
        catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Override
    public void close() {
        registry.close();
    }
}
