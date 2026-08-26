## MODIFIED Requirements

### Requirement: Datasource allowlist
When the web-sql provider is active and `ai.datasource.web-sql.allowed-datasource-ids` is non-empty, the system MUST reject any `datasourceId` that is not in that list with structured `DATASOURCE_NOT_ALLOWED` and MUST NOT send HTTP. An empty allowlist MUST NOT restrict IDs; access is decided by the forwarded Authorization token and zorth-web-sql-service visibility.

#### Scenario: Empty allowlist does not restrict ids
- **WHEN** `allowed-datasource-ids` is empty and a tool is called with a present `datasourceId`, `database`, and Authorization
- **THEN** the adapter MAY call zorth-web-sql-service for that id and MUST NOT return `DATASOURCE_NOT_ALLOWED`

#### Scenario: Listed id is allowed
- **WHEN** the allowlist contains `ds-1` and the current `datasourceId` is `ds-1`
- **THEN** the adapter may call zorth-web-sql-service for that id

#### Scenario: Unlisted id is rejected when allowlist is set
- **WHEN** the allowlist contains `ds-1` and the current `datasourceId` is `ds-2`
- **THEN** the tool result is `DATASOURCE_NOT_ALLOWED` and no HTTP request is sent
