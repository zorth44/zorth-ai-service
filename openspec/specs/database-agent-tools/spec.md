# Database Agent Tools

## Purpose

Define the Spring AI Database Tools that wrap datasource ports for table discovery, schema inspection, SQL validation, and read-only query execution.

## Requirements

### Requirement: LangChain-equivalent database tool set
The system SHALL register Spring AI tools `listTables`, `getTableSchema`, `checkSql`, and `executeQuery` with descriptions that state what they do and when to use them. Tool methods MUST call datasource ports and MUST NOT use `DataSource`, `JdbcTemplate`, `Connection`, or `DatabaseMetaData` directly.

#### Scenario: Tools are discoverable by Spring AI
- **WHEN** Database Tools are registered on a request that includes `datasourceId`
- **THEN** Spring AI exposes `listTables`, `getTableSchema`, `checkSql`, and `executeQuery`

#### Scenario: Tools do not accept datasourceId
- **WHEN** generated tool schemas are inspected
- **THEN** none of the database tools include `datasourceId`, `database`, `userId`, `conversationId`, `requestId`, `authorization`, or `executionId` as model-visible parameters

### Requirement: listTables and getTableSchema
`listTables` SHALL return the datasource table names. `getTableSchema` SHALL accept one or more table names and return structured `TableSchema` values.

#### Scenario: listTables returns names
- **WHEN** `listTables` runs against a datasource that contains `users` and `orders`
- **THEN** the tool result includes those table names

#### Scenario: getTableSchema returns columns
- **WHEN** `getTableSchema` is called with `orders`
- **THEN** the tool result includes the orders columns, types, and primary keys

### Requirement: checkSql and executeQuery contracts
`checkSql` SHALL return a structured `SqlCheckResult`. `executeQuery` SHALL return a structured `QueryResult` on success. Both tools MUST use the current `datasourceId` from `ToolContext`.

#### Scenario: checkSql accepts a read-only query
- **WHEN** `checkSql` receives a valid `SELECT`
- **THEN** the result reports that the SQL is allowed

#### Scenario: executeQuery returns bounded rows
- **WHEN** `executeQuery` receives a valid `SELECT` after validation
- **THEN** the result contains columns, rows, `rowCount`, and `truncated`

### Requirement: Structured tool errors for the model
Database tools MUST convert failures into `{ "success": false, "errorType": "...", "message": "..." }` and MUST NOT return a Java stack trace to the model. Server logs MAY retain the full exception. Mapped `errorType` values include `SQL_EXECUTION_ERROR`, `SQL_VALIDATION_ERROR`, `SQL_TIMEOUT`, `MISSING_DATASOURCE`, `MISSING_DATABASE`, `DATASOURCE_NOT_FOUND`, `DATABASE_NOT_FOUND`, `TABLE_NOT_FOUND`, `AUTH_ERROR`, `RATE_LIMITED`, `DATASOURCE_NOT_ALLOWED`, and `INVALID_ARGUMENT`.

#### Scenario: Unknown column is structured
- **WHEN** `executeQuery` runs SQL that references an unknown column
- **THEN** the tool result is a structured `SQL_EXECUTION_ERROR` containing a short database message and no stack trace

#### Scenario: Missing datasource context is structured
- **WHEN** a database tool runs without `datasourceId` in `ToolContext`
- **THEN** the tool result is a structured `MISSING_DATASOURCE` error

#### Scenario: Remote timeout is structured
- **WHEN** zorth-web-sql-service returns `SQL_EXECUTION_TIMEOUT`
- **THEN** the tool result is a structured `SQL_TIMEOUT` error

### Requirement: Missing database and credentials are structured
Database tools MUST return `{ "success": false, "errorType": "...", "message": "..." }` for missing server context needed by the active provider. `database`, `Authorization`, `datasourceId`, `userId`, `conversationId`, and `requestId` MUST NOT appear as model-visible tool parameters.

#### Scenario: Missing database context is structured
- **WHEN** the web-sql provider is active and a database tool runs without `database` in `ToolContext`
- **THEN** the tool result is a structured `MISSING_DATABASE` error

#### Scenario: Missing authorization is structured
- **WHEN** the web-sql provider is active and a database tool runs without Authorization in `ToolContext`
- **THEN** the tool result is a structured `AUTH_ERROR`

#### Scenario: Allowlist rejection is structured
- **WHEN** the current `datasourceId` is not in `allowed-datasource-ids`
- **THEN** the tool result is a structured `DATASOURCE_NOT_ALLOWED`

### Requirement: getTableSchema call size limit
`getTableSchema` MUST reject a request that names more than the configured maximum tables per call (default 5) with `INVALID_ARGUMENT` so the model narrows the set.

#### Scenario: Six tables are rejected
- **WHEN** `getTableSchema` is called with six table names
- **THEN** the tool result is `INVALID_ARGUMENT` and no metadata fetch starts for that call

### Requirement: listTables may report truncation
When the table catalog is larger than `max-listed-tables`, `listTables` MUST return the first N names with `truncated` true and a short incomplete-list message. The JDBC adapter MUST return `truncated` false when it lists from `DatabaseMetaData`.

#### Scenario: Truncated catalog is explained
- **WHEN** more tables exist than `max-listed-tables`
- **THEN** the tool result includes the first N names and states that the list is incomplete
