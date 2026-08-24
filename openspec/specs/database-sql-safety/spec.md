# Database SQL Safety

## Purpose

Define parser-based validation that allows only single-statement read-only SQL and rejects writes, DDL, and other unsafe queries before JDBC execution.

## Requirements

### Requirement: Parser-based read-only SQL validation
The system SHALL validate SQL with a SQL parser, not a `startsWith("select")` check. Only a single read-only `SELECT` statement, including `WITH` common table expressions, is allowed.

#### Scenario: Plain SELECT is accepted
- **WHEN** `SELECT id FROM users` is validated
- **THEN** validation succeeds

#### Scenario: WITH SELECT is accepted
- **WHEN** a `WITH` query whose body is a SELECT is validated
- **THEN** validation succeeds

### Requirement: Dangerous statements are rejected
The system MUST reject `INSERT`, `UPDATE`, `DELETE`, `DROP`, `ALTER`, `TRUNCATE`, `CREATE`, `REPLACE`, `MERGE`, `GRANT`, and `REVOKE` statements, and MUST reject `SELECT INTO` or any statement that writes data.

#### Scenario: DELETE is rejected
- **WHEN** `DELETE FROM users` is validated
- **THEN** validation fails with a structured error that identifies a forbidden write or DDL operation

#### Scenario: DROP is rejected
- **WHEN** `DROP TABLE orders` is validated
- **THEN** validation fails and the statement is not executed

### Requirement: Empty, multi-statement, unparsable, and oversized SQL are rejected
The system MUST reject blank SQL, more than one statement, SQL the parser cannot parse, and SQL longer than the configured maximum length. The system MUST also reject queries that exceed the configured complexity limit.

#### Scenario: Multi-statement SQL is rejected
- **WHEN** `SELECT 1; DELETE FROM users` is validated
- **THEN** validation fails as a multi-statement query

#### Scenario: Unparsable SQL is rejected
- **WHEN** text that is not valid SQL is validated
- **THEN** validation fails with a structured parse error

#### Scenario: Empty SQL is rejected
- **WHEN** blank or whitespace-only SQL is validated
- **THEN** validation fails

### Requirement: executeQuery repeats safety checks
`executeQuery` MUST run the same safety validation before JDBC execution. The system MUST NOT assume the model called `checkSql` first.

#### Scenario: Direct DELETE through executeQuery is blocked
- **WHEN** the model calls `executeQuery` with `DELETE FROM users` and never called `checkSql`
- **THEN** the executor rejects the SQL, opens no write, and returns a structured validation error
