## Why

The SQL editor Copilot will call the existing `POST /api/v1/ai/agent` endpoint so users can generate and repair insertable SQL. Today's web-sql allowlist is fail-closed on an empty ID list, so Copilot cannot use datasources the user can already see. The Database Agent prompt also answers from query result grids, which hides the ` ```sql ` fences the editor needs for Insert.

## What Changes

- Change the web-sql datasource allowlist so an empty `allowed-datasource-ids` list imposes no extra ID restriction. Access is decided by the forwarded Bearer token and web-sql product visibility (`404 DATA_SOURCE_NOT_FOUND` → existing `DATASOURCE_NOT_FOUND`).
- Keep a non-empty allowlist as an optional local/emergency lock: IDs not listed still return structured `DATASOURCE_NOT_ALLOWED` and MUST NOT send HTTP.
- Update the Database Agent system prompt so proposed or repaired SQL is placed in Markdown ` ```sql ` fences, the user-facing answer leads with those blocks, result grids are not pasted into `content`, and failed statements are repaired as complete statements. Read-only `executeQuery` remains allowed for verification.
- Add a prompt-shape test (resource snapshot or attached-prompt assertion). Update local web-sql docs to treat user Token as authorization, not a static AI ID list.
- **Not in this change:** `mode=sql_copilot`, Agent Chat Memory, Agent SSE, structured `sqlBlocks`, new tools, longer `message` limits, or SQL service contract changes. Production `provider=jdbc` remains forbidden. `/api/v1/ai/chat` and message-only Agent requests stay unchanged.

## Capabilities

### New Capabilities

- None. Copilot reuses the existing Database Agent and web-sql provider.

### Modified Capabilities

- `web-sql-datasource`: Empty allowlist no longer fail-closes; a non-empty list remains an optional restriction.
- `database-agent-tools`: `DATASOURCE_NOT_ALLOWED` only when the optional allowlist is non-empty and the current ID is absent.
- `database-agent-execution`: Database system prompt requires SQL fences, SQL-first answers, and no result-grid dumps, while still allowing read-only verification and same-request repair.

## Impact

- `ai-datasource`: `WebSqlSettings.allows` and adapter tests.
- `ai-agent`: `database-agent-system-prompt.txt` and prompt-shape tests.
- Docs: `docs/local-web-sql.md`, comments on `application.yml`, smoke-script wording.
- API: request/response shapes unchanged. Default config `allowed-datasource-ids: []` now means unrestricted IDs rather than blocking every datasource.
- Tests: no live model calls. JDBC / H2 paths unchanged.
