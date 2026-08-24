## ADDED Requirements

### Requirement: LangChain-equivalent database tool set
The system SHALL register Spring AI tools `listTables`, `getTableSchema`, `checkSql`, and `executeQuery` with descriptions that state what they do and when to use them. Tool methods MUST call datasource services and MUST NOT use `DataSource`, `JdbcTemplate`, `Connection`, or `DatabaseMetaData` directly.

#### Scenario: Tools are discoverable by Spring AI
- **WHEN** Database Tools are registered on a request that includes `datasourceId`
- **THEN** Spring AI exposes `listTables`, `getTableSchema`, `checkSql`, and `executeQuery`

#### Scenario: Tools do not accept datasourceId
- **WHEN** generated tool schemas are inspected
- **THEN** none of the database tools include `datasourceId`, `userId`, `conversationId`, or `requestId` as model-visible parameters

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
Database tools MUST convert failures into `{ "success": false, "errorType": "...", "message": "..." }` and MUST NOT return a Java stack trace to the model. Server logs MAY retain the full exception.

#### Scenario: Unknown column is structured
- **WHEN** `executeQuery` runs SQL that references an unknown column
- **THEN** the tool result is a structured `SQL_EXECUTION_ERROR` containing a short database message and no stack trace

#### Scenario: Missing datasource context is structured
- **WHEN** a database tool runs without `datasourceId` in `ToolContext`
- **THEN** the tool result is a structured `MISSING_DATASOURCE` error
