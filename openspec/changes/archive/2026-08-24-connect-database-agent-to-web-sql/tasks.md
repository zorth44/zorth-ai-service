## 1. Ports and JDBC Adapter

- [x] 1.1 Introduce `DatabaseMetadataPort` and `QueryExecutionPort` (plus a small call-context type for `datasourceId` / `database` / credential / `requestId`) in `ai-datasource`.
- [x] 1.2 Point existing JDBC `DatabaseMetadataService` and `QueryExecutionService` at those ports without changing H2 behavior, including `SqlLimitApplier` on the JDBC path.
- [x] 1.3 Switch `DatabaseTools` to depend on the ports; keep the four `@Tool` method signatures.

## 2. Agent Context, Token, and Audit

- [x] 2.1 Add optional `database` to `AgentRequest`, `ToolContextKeys`, and `AgentContext`; copy it in `SpringAiAgentService` without exposing it on tool schemas.
- [x] 2.2 Capture inbound `Authorization` on `POST /api/v1/ai/agent` and pass it into `ToolContext` without adding it to `AgentRequest`; message-only and chat stay header-optional.
- [x] 2.3 Extend `DatabaseToolAudit` with `database` and `executionId`; add a test that a Bearer value in context never appears in the audit line.

## 3. Web-sql Client and Adapters

- [x] 3.1 Add `spring-web` to `ai-datasource` and implement `WebSqlServiceClient` (base URL, connect/read timeouts, `Authorization`, `X-Request-Id`, `{code,message,details}` error mapping).
- [x] 3.2 Implement `WebSqlMetadataAdapter`: allowlist, required database/token, tables pagination with truncation, serial table-detail capped at 5, `TableSchema` mapping.
- [x] 3.3 Implement `WebSqlQueryAdapter`: `requireValid` first, UUID `executionId`, POST with `readOnly=true` / `source=AI_AGENT` / `timeoutSeconds` / `rowLimit`, no `SqlLimitApplier`, 2D rows → `QueryResult`, keep numeric strings, local `max-result-bytes` cap.

## 4. Server Wiring and Prompts

- [x] 4.1 Add `ai.datasource.provider` and `ai.datasource.web-sql.*` properties (empty allowlist, read timeout = query timeout + 10). Default provider `web-sql`.
- [x] 4.2 Wire provider-selected port beans in `AiConfiguration`; refuse JDBC when environment is `prod`/`production`; WARN when JDBC is used outside `local`/`test`.
- [x] 4.3 Update the Database Agent system prompt: missing database/auth, string numerics, aggregate in SQL. Do not change `/api/v1/ai/chat`.

## 5. Tests

- [x] 5.1 Keep H2 tests green under `provider=jdbc` (metadata, Guard, `executeQuery` write block, existing multi-step agent tests).
- [x] 5.2 Add stubbed HTTP tests: Guard blocks HTTP on DELETE; execute sends required flags; row mapping; allowlist / missing database / missing token send no HTTP; error map for `SQL_EXECUTION_FAILED`, `READ_ONLY_VIOLATION`, `SQL_EXECUTION_TIMEOUT`, `EXECUTION_LIMIT_EXCEEDED`.
- [x] 5.3 Extend controller/schema tests for optional `database`, Authorization forwarding, and unchanged message-only plus chat behavior; run `mvn clean test`.
