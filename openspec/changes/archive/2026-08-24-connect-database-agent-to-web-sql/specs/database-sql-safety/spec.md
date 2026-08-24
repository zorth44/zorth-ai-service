## ADDED Requirements

### Requirement: Web-sql path does not rewrite SQL
When the web-sql provider is active, `executeQuery` MUST NOT use `SqlLimitApplier` or otherwise mutate the validated SQL before sending it. Row limits MUST be requested as `rowLimit` on the remote execution API.

#### Scenario: LIMIT is not injected
- **WHEN** the model executes `SELECT id FROM users` on the web-sql provider
- **THEN** the HTTP `statement` is that SQL unchanged

## MODIFIED Requirements

### Requirement: executeQuery repeats safety checks
`executeQuery` MUST run the same safety validation before any provider executes SQL, including JDBC and web-sql HTTP. The system MUST NOT assume the model called `checkSql` first. Validation failure MUST NOT open a JDBC connection and MUST NOT send an HTTP execution request.

#### Scenario: Direct DELETE through executeQuery is blocked
- **WHEN** the model calls `executeQuery` with `DELETE FROM users` and never called `checkSql`
- **THEN** the executor rejects the SQL, opens no write, and returns a structured validation error

#### Scenario: Direct DELETE does not call web-sql
- **WHEN** the web-sql provider is active and the model calls `executeQuery` with `DELETE FROM users`
- **THEN** no HTTP request is sent to zorth-web-sql-service
