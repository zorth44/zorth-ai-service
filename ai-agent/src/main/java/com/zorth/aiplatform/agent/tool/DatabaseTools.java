package com.zorth.aiplatform.agent.tool;

import com.zorth.aiplatform.agent.support.AgentContext;
import com.zorth.aiplatform.agent.support.DatabaseToolAudit;
import com.zorth.aiplatform.agent.support.ToolExecutionSupport;
import com.zorth.aiplatform.datasource.exception.DatasourceException;
import com.zorth.aiplatform.datasource.model.DatabaseToolFailure;
import com.zorth.aiplatform.datasource.model.QueryResult;
import com.zorth.aiplatform.datasource.model.SqlCheckResult;
import com.zorth.aiplatform.datasource.port.DatabaseMetadataPort;
import com.zorth.aiplatform.datasource.port.DatasourceCall;
import com.zorth.aiplatform.datasource.port.QueryExecutionPort;
import com.zorth.aiplatform.datasource.service.SqlValidationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public final class DatabaseTools {

    private static final Logger log = LoggerFactory.getLogger(DatabaseTools.class);
    private static final int DEFAULT_MAX_TABLES_PER_SCHEMA_CALL = 5;

    private final DatabaseMetadataPort metadataPort;
    private final SqlValidationService validationService;
    private final QueryExecutionPort queryExecutionPort;
    private final DatabaseToolAudit audit;
    private final ToolExecutionSupport executionSupport;
    private final int maxTablesPerSchemaCall;

    public DatabaseTools(
            DatabaseMetadataPort metadataPort,
            SqlValidationService validationService,
            QueryExecutionPort queryExecutionPort,
            DatabaseToolAudit audit,
            ToolExecutionSupport executionSupport) {
        this(
                metadataPort,
                validationService,
                queryExecutionPort,
                audit,
                executionSupport,
                DEFAULT_MAX_TABLES_PER_SCHEMA_CALL);
    }

    public DatabaseTools(
            DatabaseMetadataPort metadataPort,
            SqlValidationService validationService,
            QueryExecutionPort queryExecutionPort,
            DatabaseToolAudit audit,
            ToolExecutionSupport executionSupport,
            int maxTablesPerSchemaCall) {
        this.metadataPort = Objects.requireNonNull(metadataPort, "metadataPort must not be null");
        this.validationService = Objects.requireNonNull(validationService,
                "validationService must not be null");
        this.queryExecutionPort = Objects.requireNonNull(queryExecutionPort,
                "queryExecutionPort must not be null");
        this.audit = Objects.requireNonNull(audit, "audit must not be null");
        this.executionSupport = Objects.requireNonNull(executionSupport,
                "executionSupport must not be null");
        if (maxTablesPerSchemaCall <= 0) {
            throw new IllegalArgumentException("maxTablesPerSchemaCall must be positive");
        }
        this.maxTablesPerSchemaCall = maxTablesPerSchemaCall;
    }

    @Tool(description = """
            List tables available in the current datasource.
            Use this tool when you need to discover which tables exist
            before answering a database-related question.
            """)
    public Object listTables(ToolContext toolContext) {
        return execute("listTables", toolContext, null, null, () -> {
            return metadataPort.listTables(call(toolContext, null));
        });
    }

    @Tool(description = """
            Get schema information for one or more database tables.
            Returns columns, data types, primary keys and comments.
            Use this tool before generating SQL when table structure is unknown.
            """)
    public Object getTableSchema(
            @ToolParam(description = "One table name or a comma-separated list of table names",
                    required = true)
            String tableNames,
            ToolContext toolContext) {
        return execute("getTableSchema", toolContext, tableNames, null, () -> {
            List<String> names = splitTableNames(tableNames);
            if (names.size() > maxTablesPerSchemaCall) {
                throw new DatasourceException(
                        "INVALID_ARGUMENT",
                        "Request at most " + maxTablesPerSchemaCall
                                + " tables per getTableSchema call");
            }
            return metadataPort.getTableSchemas(call(toolContext, null), names);
        });
    }

    @Tool(description = """
            Validate a SQL query before execution.
            Only read-only SELECT queries are allowed.
            Use this tool before executing generated SQL.
            """)
    public Object checkSql(
            @ToolParam(description = "SQL query to validate", required = true)
            String sql,
            ToolContext toolContext) {
        return execute("checkSql", toolContext, sql, null, () -> {
            requireDatasourceId(toolContext);
            return validationService.check(sql);
        });
    }

    @Tool(description = """
            Execute a validated read-only SQL query against the current datasource.
            Only SELECT queries are allowed.
            Use this tool after SQL validation.
            """)
    public Object executeQuery(
            @ToolParam(description = "Read-only SQL query to execute", required = true)
            String sql,
            ToolContext toolContext) {
        String executionId = UUID.randomUUID().toString();
        return execute("executeQuery", toolContext, sql, executionId, () -> {
            return queryExecutionPort.execute(call(toolContext, executionId), sql);
        });
    }

    private Object execute(
            String toolName,
            ToolContext toolContext,
            String arguments,
            String executionId,
            ToolAction action) {
        long startedAt = System.nanoTime();
        try {
            Object result = executionSupport.execute(toolName, toolContext, () -> {
                try {
                    return action.run();
                }
                catch (DatasourceException exception) {
                    log.error("Database tool {} failed: {}", toolName, exception.getMessage(),
                            exception);
                    return new DatabaseToolFailure(exception.errorType(), exception.getMessage());
                }
                catch (RuntimeException exception) {
                    log.error("Database tool {} failed unexpectedly", toolName, exception);
                    return new DatabaseToolFailure(
                            "SQL_EXECUTION_ERROR",
                            "Database tool execution failed");
                }
            });
            audit.record(
                    toolContext,
                    toolName,
                    arguments,
                    executionId,
                    statusOf(result),
                    elapsedMillis(startedAt),
                    rowCountOf(result),
                    errorMessageOf(result));
            return result;
        }
        catch (RuntimeException exception) {
            audit.record(
                    toolContext,
                    toolName,
                    arguments,
                    executionId,
                    "FAILURE",
                    elapsedMillis(startedAt),
                    null,
                    exception.getMessage());
            throw exception;
        }
    }

    private static DatasourceCall call(ToolContext toolContext, String executionId) {
        return new DatasourceCall(
                requireDatasourceId(toolContext),
                AgentContext.database(toolContext),
                AgentContext.authorization(toolContext),
                AgentContext.requestId(toolContext),
                executionId);
    }

    private static String requireDatasourceId(ToolContext toolContext) {
        String datasourceId = AgentContext.datasourceId(toolContext);
        if (datasourceId == null) {
            throw new DatasourceException(
                    "MISSING_DATASOURCE",
                    "datasourceId is not available in the current agent context");
        }
        return datasourceId;
    }

    private static List<String> splitTableNames(String tableNames) {
        if (tableNames == null || tableNames.isBlank()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String part : tableNames.split(",")) {
            if (!part.isBlank()) {
                names.add(part.trim());
            }
        }
        return names;
    }

    private static String statusOf(Object result) {
        if (result instanceof DatabaseToolFailure) {
            return "FAILURE";
        }
        if (result instanceof SqlCheckResult checkResult) {
            return checkResult.valid() ? "SUCCESS" : "FAILURE";
        }
        return "SUCCESS";
    }

    private static Integer rowCountOf(Object result) {
        if (result instanceof QueryResult queryResult) {
            return queryResult.rowCount();
        }
        return null;
    }

    private static String errorMessageOf(Object result) {
        if (result instanceof DatabaseToolFailure failure) {
            return failure.message();
        }
        if (result instanceof SqlCheckResult checkResult && !checkResult.valid()) {
            return checkResult.message();
        }
        return null;
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    @FunctionalInterface
    private interface ToolAction {
        Object run();
    }
}
