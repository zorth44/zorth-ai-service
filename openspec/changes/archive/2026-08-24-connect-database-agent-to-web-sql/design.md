## Context

Database Agent tools already exist: `listTables`, `getTableSchema`, `checkSql`, `executeQuery`. They call `DatabaseMetadataService` and `QueryExecutionService`, which talk JDBC through a named `DatasourceRegistry`. `checkSql` / `executeQuery` share `SqlValidationService` (JSqlParser). `AgentRequest` has `datasourceId` but no `database`. The agent endpoint does not read `Authorization`.

zorth-web-sql-service now owns data sources, pools, and execution history. Commit `0746851` added optional fields on `POST /api/v1/sql/executions`: `readOnly`, `timeoutSeconds`, `source`. Token passthrough is the accepted auth model. Their `fail-on-unknown-properties` is true, so those fields had to land there before this service could send them.

Constraints that still hold: no custom tool-calling loop; the model never generates `datasourceId`, `database`, or credentials; `checkSql` stays in AI; query rows are not persisted.

## Goals / Non-Goals

**Goals:**

- Default Database Agent metadata and query execution to zorth-web-sql-service over HTTP.
- Keep JDBC as a local/test adapter; refuse it in production.
- Always send `readOnly=true`, `source=AI_AGENT`, and a short `timeoutSeconds` on execute.
- Forward the user Bearer token in memory only.
- Keep the four tool method signatures and `QueryResult` / `TableSchema` / `SqlCheckResult` shapes.
- Keep JSqlParser as the AI-side safety boundary, because their read-only check is first-keyword classification.

**Non-Goals:**

- `listDatabases`, batch table-detail, comment search, or `searchTables`.
- Lifting `allowed-datasource-ids`.
- AI-owned login or a service-to-service credential.
- Changing `/api/v1/ai/chat` or message-only `/api/v1/ai/agent`.
- Oracle / SQL Server. They only register MySQL, PostgreSQL, and GBase 8a.

## Decisions

### 1. Ports in `ai-datasource`; two adapters; tools stay on ports

```text
DatabaseTools
  ├── checkSql ──► SqlValidationService
  └── listTables / getTableSchema / executeQuery
           │
           ▼
    DatabaseMetadataPort / QueryExecutionPort
           │
     ┌─────┴──────┐
     ▼            ▼
  jdbc/        websql/
  (H2 tests)   (default)
```

`DatabaseTools` takes the ports, not the JDBC services. Existing JDBC classes become the jdbc adapter (or thin wrappers around them). Web-sql adapters call `WebSqlServiceClient`.

Rejected: deleting JDBC in this change. Tests and local work must not need the other service. Rejected: putting `@Tool` HTTP calls in `ai-server`. Tools would then mix transport with agent API.

### 2. Default provider is `web-sql`; JDBC is local/test only

```yaml
ai:
  datasource:
    provider: web-sql   # jdbc | web-sql
    web-sql:
      base-url: ${WEB_SQL_BASE_URL:http://localhost:8080}
      connect-timeout-seconds: 5
      read-timeout-seconds: 20
      max-tables-per-schema-call: 5
      max-listed-tables: 200
      allowed-datasource-ids: []
```

`allowed-datasource-ids` empty means fail closed: every web-sql call returns `DATASOURCE_NOT_ALLOWED` until ids are listed.

On startup:

- `provider=jdbc` and `ai.platform.environment` in `{prod, production}` → refuse to start.
- `provider=jdbc` and environment not in `{local, test}` → WARN that this bypasses the other service's auth and audit.

Tests set `ai.datasource.provider=jdbc`. No live web-sql in default `mvn test`.

### 3. Merge original Phase 1 and Phase 2 on the wire

Because the execution API already accepts the new fields, this change sends them from day one:

| Field | Value |
| --- | --- |
| `executionId` | UUID generated here, also written to AI audit |
| `dataSourceId` | `ToolContext` |
| `database` | `ToolContext` |
| `statement` | model SQL after Guard |
| `rowLimit` | `ai.agent.database.max-rows` (200) |
| `readOnly` | `true` |
| `source` | `AI_AGENT` |
| `timeoutSeconds` | `ai.agent.database.query-timeout-seconds` (10) |

Headers: `Authorization` copied from the inbound agent request; `X-Request-Id` = AI `requestId`.

Their async HTTP timeout is `timeoutSeconds + 5`. Client read timeout is therefore `query-timeout-seconds + 10` (default 20), not the 70 seconds from the earlier plan that assumed a fixed 60s server timeout.

Do not send these fields on metadata GETs.

### 4. SQL Guard stays; `SqlLimitApplier` is JDBC-only

`WebSqlQueryAdapter.execute`:

1. `SqlValidationService.requireValid(sql)` — if this fails, no HTTP.
2. Allowlist / database / token checks.
3. POST executions.
4. Map 2D `rows` onto `QueryResult` using `columns[].label` (fallback `name`).
5. Keep BIGINT/DECIMAL strings; do not coerce to numbers.
6. After mapping, still enforce `max-result-bytes` as a local cap.

Their classifier allows `SHOW` / `EXPLAIN` / `DESC` as "SELECT". Ours does not. Fail closed on our parser; do not widen Guard to match them in this change.

JDBC adapter keeps `SqlLimitApplier`. Web-sql adapter does not rewrite SQL.

### 5. `database` is server context, same as `datasourceId`

Add optional `database` to `AgentRequest`. Copy into `ToolContext` under `ToolContextKeys.DATABASE`. The model does not get it as a tool argument.

Web-sql adapter: missing/blank `database` → structured `MISSING_DATABASE`, no HTTP. JDBC adapter: ignore `database` (H2 catalog comes from the connection).

Frontend already has `?dataSourceId=&database=`; the same pair is the intended agent payload.

### 6. Capture `Authorization` outside `AgentRequest`

`AiAgentService.execute` cannot grow a token field on `AgentRequest` (that record is logged/serialized in tests and must stay free of secrets).

```text
POST /api/v1/ai/agent
  Header Authorization: Bearer <user-token>   // required only when datasourceId is present for a useful DB call
  Body { message, conversationId?, datasourceId?, database?, userId? }
```

Controller reads the header and passes a runtime object (not the request DTO) into `SpringAiAgentService`. That value goes into `ToolContext` under a credential key. `DatabaseToolAudit` MUST NOT read or print it. Missing token on a web-sql tool call → `AUTH_ERROR` from the tool, not HTTP 401/500 from `/agent`. Message-only agent and chat stay anonymous.

Three hard rules: in memory only; never logs; never `AgentRequest` / tool schema.

Their `userId` comes from the token. Our optional `userId` stays correlation-only and is not sent to web-sql.

### 7. Metadata: page, then serial table-detail

- `listTables`: `GET /api/v1/data-sources/{id}/tables?database=&types=&pageSize=200`. `types=TABLE` when `include-views=false`, else `TABLE,VIEW`. Stop at `max-listed-tables`. If a next page remains, return names plus `truncated=true` and a short message. Do not dump thousands of names into context.
- `getTableSchema`: at most `max-tables-per-schema-call` (5) names; more → `INVALID_ARGUMENT` telling the model to narrow. Serial `GET .../table-detail` so we do not consume their per-user execution quota (metadata is not in that quota, but serial still avoids stampedes). Map `ColumnItem.typeName` → `ColumnSchema.dataType`, `nullable`, `comment`; PK from `primaryKey.columns`. Ignore `ddl` / `indexes`. `table-detail` has no table comment: if `TableSchema.comment` is needed, one extra list call with `keyword=<table>` is allowed; skip it when the list page already has the name.

### 8. Error mapping is HTTP status + their `code`

Their body is `{requestId, code, message, details}`. Truncate `message` (e.g. 1000 chars). Do not parse vendor text. For `422 SQL_EXECUTION_FAILED`, copy `details.sqlState` and `details.vendorErrorCode` into the tool message so Case 4 repair still works.

| Their status / code | `errorType` | Model can self-fix |
| --- | --- | --- |
| 404 `DATA_SOURCE_NOT_FOUND` | `DATASOURCE_NOT_FOUND` | no |
| 404 `DATABASE_NOT_FOUND` | `DATABASE_NOT_FOUND` | no |
| 404 `TABLE_NOT_FOUND` | `TABLE_NOT_FOUND` | yes, re-list |
| 422 `SQL_EXECUTION_FAILED` | `SQL_EXECUTION_ERROR` | yes |
| 422 `READ_ONLY_VIOLATION` | `SQL_VALIDATION_ERROR` | no |
| 504 `SQL_EXECUTION_TIMEOUT` | `SQL_TIMEOUT` | yes, shrink |
| 429 `EXECUTION_LIMIT_EXCEEDED` | `RATE_LIMITED` | no |
| 401 / 503 auth | `AUTH_ERROR` | no |
| 400 `MULTI_STATEMENT_NOT_SUPPORTED` | `SQL_VALIDATION_ERROR` | Guard should have caught this |
| 400 `VALIDATION_FAILED` on `database` | `MISSING_DATABASE` | no |
| our allowlist miss | `DATASOURCE_NOT_ALLOWED` | no |
| our Guard reject | `SQL_VALIDATION_ERROR` | yes if the SQL was malformed/read-write |

Network/5xx other than mapped codes → `SQL_EXECUTION_ERROR` with a generic message, full exception only in server logs.

### 9. RestClient lives in `ai-datasource`; bean wired in `ai-server`

`ai-datasource` currently has no Spring. Adding `spring-web` for `RestClient` is accepted so URL, headers, timeouts, and JSON sit next to the adapters. `AiConfiguration` builds the `RestClient` (connect 5s, read from config) and the provider-selected port beans.

Contract tests use `MockRestServiceServer` (or an equivalent stub). They MUST NOT call a real web-sql or a real model.

### 10. Audit gains `executionId` and `database`

`DatabaseToolAudit` logs `executionId` for `executeQuery` (the UUID we sent) and `database`. Assert in a test that a Bearer value present in `ToolContext` does not appear in the formatted audit message.

## Risks / Trade-offs

- [Their read-only is first-keyword, not a parser] → Keep JSqlParser; always send `readOnly=true`; keep allowlist fail closed until datasource accounts are confirmed read-only.
- [Allowlist empty blocks all web-sql use] → Intentional. Operators must list ids. JDBC tests unaffected.
- [Token in ToolContext could leak via generic dumps] → Dedicated credential key; audit/support loggers never print context map; test the audit line.
- [String numerics confuse the model] → Prompt: aggregate in SQL, do not arithmetic on result strings.
- [Serial table-detail is slower] → Cap at 5; avoid their per-user limit of 3 on execute; metadata is not in that quota.
- [No test environment yet] → Ship against stubs; live wiring waits on their URL and a confirmed `dataSourceId`.
- [JDBC leftover in production] → Startup refuse on prod/production.

## Migration Plan

1. Land ports + JDBC adapter + `database` context. Existing H2 tests keep passing under `provider=jdbc`.
2. Land web-sql client, mapping, allowlist, token forwarding, stubbed contract tests.
3. Default config `provider=web-sql` with empty allowlist (safe if mis-deployed: tools return `DATASOURCE_NOT_ALLOWED`).
4. Operators set `WEB_SQL_BASE_URL` and `allowed-datasource-ids` when a test/prod source exists.
5. Rollback: set `provider=jdbc` only in local/test, or revert the change. No persistent AI-side schema.

## Open Questions

- Live `WEB_SQL_BASE_URL`, a confirmed read-only `dataSourceId`, and a test user token are still missing. They do not block this implementation; they block the first real call.
