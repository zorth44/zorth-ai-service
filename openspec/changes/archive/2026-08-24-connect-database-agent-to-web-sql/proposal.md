## Why

Database Agent currently holds JDBC accounts and runs queries itself. zorth-web-sql-service already owns data-source permissions, connection pools, and execution history, and it now accepts Agent calls on the existing execution API (`readOnly`, `timeoutSeconds`, `source=AI_AGENT`). Keeping a second JDBC path would duplicate access control and audit. Switch metadata and query execution to that service now, while SQL Guard stays in AI.

## What Changes

- Split `ai-datasource` into ports plus two adapters: HTTP to zorth-web-sql-service (default) and the existing JDBC implementation (local and tests only).
- Route `listTables`, `getTableSchema`, and `executeQuery` through the HTTP adapter. `checkSql` stays on `SqlValidationService`.
- `executeQuery` always runs the AI SQL Guard first, then calls `POST /api/v1/sql/executions` with `readOnly=true`, `source=AI_AGENT`, `timeoutSeconds`, and `rowLimit`.
- Extend `AgentRequest` with optional `database`. Copy it into `ToolContext` with `datasourceId`; the model does not generate either.
- When a request includes `datasourceId`, accept the caller's `Authorization` header and forward it in memory only. Do not put the token on `AgentRequest`, in logs, or in tool schemas.
- Map web-sql HTTP errors and 2D result rows onto the existing `DatabaseToolFailure` / `QueryResult` contracts.
- Fail closed on `allowed-datasource-ids`. Empty means no web-sql datasource is allowed.
- Production profile MUST refuse `ai.datasource.provider=jdbc`.

## Capabilities

### New Capabilities

- `web-sql-datasource`: HTTP client to zorth-web-sql-service for metadata and read-only execution, including token forwarding, request mapping, result conversion, error mapping, and datasource allowlist.

### Modified Capabilities

- `datasource-operations`: Metadata and query execution become provider-selected ports. JDBC remains a local/test adapter, not the production default.
- `database-sql-safety`: `executeQuery` must re-validate SQL before any provider runs it, not only before JDBC. Web-sql path must not rewrite SQL with `SqlLimitApplier`.
- `database-agent-tools`: Tools stay the same four methods, but must handle missing `database`, missing credentials, allowlist rejection, and web-sql error types as structured failures.
- `database-agent-execution`: Agent context gains `database` and in-memory credentials. Audit gains `executionId` and `database` and MUST NOT log credentials.

## Impact

- `ai-datasource` adds RestClient-based web-sql code; JDBC registry stays for tests.
- `ai-agent` / `ai-server` add `database` on the agent request, Authorization capture, provider beans, and web-sql configuration.
- `POST /api/v1/ai/agent` with `datasourceId` requires `Authorization`; message-only agent and `/api/v1/ai/chat` stay anonymous.
- Tests: keep H2 for JDBC/Guard; add stubbed HTTP contract tests for web-sql. No live model calls.
- Out of scope: `listDatabases`, batch table-detail, search by comment, lifting the allowlist, Oracle/SQL Server, conversation persistence, AI-owned login.
