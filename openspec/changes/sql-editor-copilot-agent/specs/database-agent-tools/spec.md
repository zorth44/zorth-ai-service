## MODIFIED Requirements

### Requirement: Missing database and credentials are structured
Database tools MUST return `{ "success": false, "errorType": "...", "message": "..." }` for missing server context needed by the active provider. `database`, `Authorization`, `datasourceId`, `userId`, `conversationId`, and `requestId` MUST NOT appear as model-visible tool parameters.

#### Scenario: Missing database context is structured
- **WHEN** the web-sql provider is active and a database tool runs without `database` in `ToolContext`
- **THEN** the tool result is a structured `MISSING_DATABASE` error

#### Scenario: Missing authorization is structured
- **WHEN** the web-sql provider is active and a database tool runs without Authorization in `ToolContext`
- **THEN** the tool result is a structured `AUTH_ERROR`

#### Scenario: Allowlist rejection is structured
- **WHEN** `allowed-datasource-ids` is non-empty and the current `datasourceId` is not in that list
- **THEN** the tool result is a structured `DATASOURCE_NOT_ALLOWED`

#### Scenario: Empty allowlist is not treated as rejection
- **WHEN** `allowed-datasource-ids` is empty and a database tool runs with `datasourceId`, `database`, and Authorization
- **THEN** the tool result is not `DATASOURCE_NOT_ALLOWED`
