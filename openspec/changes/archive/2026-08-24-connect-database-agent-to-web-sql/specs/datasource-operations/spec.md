## ADDED Requirements

### Requirement: Datasource provider selection
The system SHALL select the metadata and query adapters with `ai.datasource.provider` of `jdbc` or `web-sql`. The default SHALL be `web-sql`. Database tools MUST call ports, not JDBC types.

#### Scenario: Default provider is web-sql
- **WHEN** the application starts without an explicit provider
- **THEN** metadata and `executeQuery` use the web-sql HTTP adapters

#### Scenario: Tests can use JDBC
- **WHEN** `ai.datasource.provider` is `jdbc`
- **THEN** metadata and `executeQuery` use the existing JDBC adapters

#### Scenario: Production refuses JDBC
- **WHEN** `ai.datasource.provider` is `jdbc` and `ai.platform.environment` is `prod` or `production`
- **THEN** the application MUST fail to start

## MODIFIED Requirements

### Requirement: Named datasource registry
The JDBC adapter SHALL resolve a server-supplied `datasourceId` to a configured JDBC datasource when `ai.datasource.provider` is `jdbc`. The registry MUST create connections only for ids that exist in configuration and MUST NOT accept a JDBC URL or credentials from the model. When the provider is `web-sql`, the system MUST NOT open JDBC connections from `ai.datasources`.

#### Scenario: Known datasource is resolved
- **WHEN** the JDBC provider is active and a configured `datasourceId` is requested
- **THEN** the registry returns a usable JDBC `DataSource` for that id

#### Scenario: Unknown datasource is rejected
- **WHEN** the JDBC provider is active and a `datasourceId` that is not in configuration is requested
- **THEN** the lookup fails with a structured not-found error and no JDBC connection is opened

#### Scenario: Web-sql does not use the JDBC registry
- **WHEN** the web-sql provider is active
- **THEN** `listTables`, `getTableSchema`, and `executeQuery` do not open a local JDBC connection

### Requirement: Table listing from JDBC metadata
When the JDBC provider is active, the system SHALL list tables the current datasource can access by using JDBC `DatabaseMetaData`. The first phase MUST include `TABLE` objects. Views MAY be included only when configuration enables them.

#### Scenario: Tables are listed
- **WHEN** the JDBC provider is active and metadata is requested for a datasource that contains user tables
- **THEN** the service returns those table names and does not return raw JDBC metadata objects

#### Scenario: Views follow configuration
- **WHEN** the JDBC provider is active and view inclusion is disabled
- **THEN** the table list does not include `VIEW` objects

### Requirement: Bounded read-only query execution
The system SHALL execute only previously validated read-only SQL against the current datasource, apply configured max rows, query timeout, and max result size, and return a `QueryResult` with columns, rows as `List<Map<String, Object>>`, row count, truncation flag, and an optional message. When the JDBC provider is active, missing SQL limits MAY be applied by rewriting the statement. When the web-sql provider is active, the system MUST NOT rewrite SQL to inject `LIMIT` and MUST pass `rowLimit` to the remote service instead.

#### Scenario: Successful query
- **WHEN** a valid read-only query returns two rows
- **THEN** the result contains column names, those two rows as JSON-safe values, `rowCount` 2, and `truncated` false

#### Scenario: Missing limit is applied on JDBC
- **WHEN** the JDBC provider is active, a valid SELECT has no row limit, and the result would exceed `max-rows`
- **THEN** the executor restricts the result and sets `truncated` true

#### Scenario: Oversized payload is truncated
- **WHEN** serialized result size would exceed `max-result-bytes`
- **THEN** the executor stops adding rows, sets `truncated` true, and includes a short explanation
