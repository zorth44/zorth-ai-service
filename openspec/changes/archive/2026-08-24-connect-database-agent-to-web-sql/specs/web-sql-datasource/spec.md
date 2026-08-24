## ADDED Requirements

### Requirement: Web-sql HTTP client
The web-sql provider SHALL call zorth-web-sql-service with `RestClient`. It MUST send `Authorization` from `ToolContext` and `X-Request-Id` equal to the AI `requestId`. Connect timeout defaults to 5 seconds. Read timeout defaults to `query-timeout-seconds + 10` seconds. The client MUST NOT log the Authorization value.

#### Scenario: Execute forwards identity headers
- **WHEN** `executeQuery` runs on the web-sql provider with a Bearer token and request id `req-1`
- **THEN** the HTTP request includes `Authorization` copied from context and `X-Request-Id: req-1`

#### Scenario: Read timeout tracks query timeout
- **WHEN** `query-timeout-seconds` is 10
- **THEN** the HTTP read timeout is 20 seconds

### Requirement: Datasource allowlist
When the web-sql provider is active, the system MUST reject any `datasourceId` that is not in `ai.datasource.web-sql.allowed-datasource-ids` with structured `DATASOURCE_NOT_ALLOWED` and MUST NOT send HTTP. An empty allowlist allows no ids.

#### Scenario: Empty allowlist is fail closed
- **WHEN** `allowed-datasource-ids` is empty and a tool is called with any `datasourceId`
- **THEN** the tool result is `DATASOURCE_NOT_ALLOWED` and no HTTP request is sent

#### Scenario: Listed id is allowed
- **WHEN** the allowlist contains `ds-1` and the current `datasourceId` is `ds-1`
- **THEN** the adapter may call zorth-web-sql-service for that id

### Requirement: Required database and token on web-sql
Web-sql metadata and execute calls MUST include `database` from `ToolContext`. Missing `database` SHALL return `MISSING_DATABASE` without HTTP. Missing `Authorization` SHALL return `AUTH_ERROR` without HTTP.

#### Scenario: Missing database is structured
- **WHEN** a web-sql tool runs with `datasourceId` but no `database`
- **THEN** the tool result is `MISSING_DATABASE` and no HTTP request is sent

#### Scenario: Missing token is structured
- **WHEN** a web-sql tool runs without an Authorization value in context
- **THEN** the tool result is `AUTH_ERROR` and no HTTP request is sent

### Requirement: listTables via tables API
`listTables` on the web-sql provider SHALL call `GET /api/v1/data-sources/{id}/tables` with the current `database`, cursor pagination, `pageSize` 200, and `types=TABLE` unless view inclusion is enabled. It MUST stop at `max-listed-tables` (default 200). If more tables remain, the result MUST set `truncated` true and include a short incomplete-list message.

#### Scenario: First page of tables is returned
- **WHEN** the remote tables API returns `users` and `orders` with no further page
- **THEN** the tool result includes those table names and `truncated` is false

#### Scenario: Excess tables are truncated
- **WHEN** more than `max-listed-tables` tables exist
- **THEN** only the first N names are returned and the result explains that the list is incomplete

### Requirement: getTableSchema via table-detail
`getTableSchema` on the web-sql provider SHALL call `GET /api/v1/data-sources/{id}/table-detail` once per table, serially, with the current `database`. A single call MUST reject more than `max-tables-per-schema-call` (default 5) names with `INVALID_ARGUMENT`. The adapter SHALL map columns and primary keys onto `TableSchema` and MUST NOT return `ddl` or index lists to the model.

#### Scenario: One table is mapped
- **WHEN** table-detail returns columns and a primary key for `orders`
- **THEN** the tool result is a `TableSchema` with those column names, types, nullability, comments, and primary-key names

#### Scenario: Too many tables are rejected
- **WHEN** the model asks for schema of 6 tables in one call
- **THEN** the tool result is `INVALID_ARGUMENT` and no table-detail HTTP is sent

### Requirement: executeQuery via executions API
`executeQuery` on the web-sql provider MUST call `SqlValidationService.requireValid` first. Only then MAY it `POST /api/v1/sql/executions` with `executionId` (UUID), `dataSourceId`, `database`, `statement`, `rowLimit` equal to `max-rows`, `readOnly` true, `source` `AI_AGENT`, and `timeoutSeconds` equal to `query-timeout-seconds`. It MUST NOT apply `SqlLimitApplier`.

#### Scenario: Guard failure never leaves the process
- **WHEN** the model calls `executeQuery` with `DELETE FROM users`
- **THEN** validation fails with `SQL_VALIDATION_ERROR` and no HTTP request is sent

#### Scenario: Successful execute maps rows
- **WHEN** the remote response is `kind=RESULT_SET` with columns and two array rows
- **THEN** the tool result is a `QueryResult` of two maps keyed by column label, with `rowCount` 2 and remote `truncated` preserved

#### Scenario: Required execute flags are sent
- **WHEN** a validated SELECT is executed
- **THEN** the JSON body includes `readOnly` true, `source` `AI_AGENT`, `timeoutSeconds` from config, and `rowLimit` from `max-rows`

### Requirement: Numeric and binary values pass through
BIGINT and DECIMAL values received as strings MUST remain strings. BLOB placeholders of the form `{binary:true,size,base64:null}` MUST be passed through. The adapter MUST NOT parse those strings into numeric types.

#### Scenario: Decimal string is kept
- **WHEN** a cell is the string `"12.50"`
- **THEN** `QueryResult` stores `"12.50"`, not a floating-point number

### Requirement: Remote error mapping
The web-sql client SHALL map remote `{code,message,details}` onto `DatabaseToolFailure.errorType` without Java stack traces. `SQL_EXECUTION_FAILED` messages MAY include `sqlState` and `vendorErrorCode` from `details`, truncated to a safe length.

#### Scenario: Unknown column is repairable
- **WHEN** the remote service returns 422 `SQL_EXECUTION_FAILED` with message `Unknown column 'order_time'`
- **THEN** the tool result is `SQL_EXECUTION_ERROR` containing that message and no stack trace

#### Scenario: Read-only violation is not treated as a SQL typo
- **WHEN** the remote service returns 422 `READ_ONLY_VIOLATION`
- **THEN** the tool result is `SQL_VALIDATION_ERROR`

#### Scenario: Timeout is structured
- **WHEN** the remote service returns 504 `SQL_EXECUTION_TIMEOUT`
- **THEN** the tool result is `SQL_TIMEOUT`

#### Scenario: Concurrent limit is structured
- **WHEN** the remote service returns 429 `EXECUTION_LIMIT_EXCEEDED`
- **THEN** the tool result is `RATE_LIMITED`
