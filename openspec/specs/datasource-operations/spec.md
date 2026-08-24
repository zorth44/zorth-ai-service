# Datasource Operations

## Purpose

Define named JDBC datasource resolution, table metadata inspection, and bounded read-only query execution behind the Database Agent.

## Requirements

### Requirement: Named datasource registry
The system SHALL resolve a server-supplied `datasourceId` to a configured JDBC datasource. The registry MUST create connections only for ids that exist in configuration and MUST NOT accept a JDBC URL or credentials from the model.

#### Scenario: Known datasource is resolved
- **WHEN** a configured `datasourceId` is requested
- **THEN** the registry returns a usable JDBC `DataSource` for that id

#### Scenario: Unknown datasource is rejected
- **WHEN** a `datasourceId` that is not in configuration is requested
- **THEN** the lookup fails with a structured not-found error and no JDBC connection is opened

### Requirement: Table listing from JDBC metadata
The system SHALL list tables the current datasource can access by using JDBC `DatabaseMetaData`. The first phase MUST include `TABLE` objects. Views MAY be included only when configuration enables them.

#### Scenario: Tables are listed
- **WHEN** metadata is requested for a datasource that contains user tables
- **THEN** the service returns those table names and does not return raw JDBC metadata objects

#### Scenario: Views follow configuration
- **WHEN** view inclusion is disabled
- **THEN** the table list does not include `VIEW` objects

### Requirement: Table schema for the model
The system SHALL return structured schema for one or more tables, including table name, table comment when available, columns (name, data type, nullability, comment), and primary keys. The result MUST NOT expose JDBC driver types to the model.

#### Scenario: Schema includes columns and keys
- **WHEN** schema is requested for an existing table
- **THEN** the result contains column names, data types, nullability, comments when the database provides them, and primary-key column names

#### Scenario: Missing table is a structured failure
- **WHEN** schema is requested for a table that does not exist
- **THEN** the service returns a structured not-found error without a Java stack trace

### Requirement: Bounded read-only query execution
The system SHALL execute only previously validated read-only SQL against the current datasource, apply configured max rows, query timeout, and max result size, and return a `QueryResult` with columns, rows, row count, truncation flag, and an optional message.

#### Scenario: Successful query
- **WHEN** a valid read-only query returns two rows
- **THEN** the result contains column names, those two rows as JSON-safe values, `rowCount` 2, and `truncated` false

#### Scenario: Missing limit is applied
- **WHEN** a valid SELECT has no row limit and the result would exceed `max-rows`
- **THEN** the executor restricts the result and sets `truncated` true

#### Scenario: Oversized payload is truncated
- **WHEN** serialized result size would exceed `max-result-bytes`
- **THEN** the executor stops adding rows, sets `truncated` true, and includes a short explanation
