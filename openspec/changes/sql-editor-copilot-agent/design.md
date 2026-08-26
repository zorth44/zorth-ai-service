## Context

The SQL editor Copilot calls the existing synchronous `POST /api/v1/ai/agent` with the user's Bearer token, `datasourceId`, `database`, and a `message` that already includes editor SQL and last-error context. Tools already forward that token to zorth-web-sql-service. Two leftovers from the first web-sql connection block product use: an empty `allowed-datasource-ids` list rejects every ID, and the Database system prompt tells the model to answer from query result grids.

The editor inserts from Markdown ` ```sql ` fences in `content`. No new HTTP contract is needed.

## Goals / Non-Goals

**Goals:**

- Let any datasource the user can see through web-sql be used by Database Tools, without a static AI ID allowlist.
- Keep an optional non-empty allowlist as a local/emergency lock.
- Shape Database Agent answers so proposed and repaired SQL is in ` ```sql ` fences and result grids stay out of `content`.
- Keep read-only `executeQuery` available for verification and same-request repair.
- Keep message-only Agent and `/api/v1/ai/chat` unchanged.

**Non-Goals:**

- `mode` on `AgentRequest`, Agent Chat Memory, Agent SSE, structured `sqlBlocks`.
- New tools, longer message limits, SQL service contract changes.
- Lifting production `provider=jdbc` prohibition.
- Changing CORS or asking the model to generate `datasourceId` / `database` / Token.

## Decisions

### 1. Empty allowlist means unrestricted IDs, not fail-closed

`WebSqlSettings.allows(datasourceId)` returns true when the configured list is empty and `datasourceId` is present. A non-empty list still requires membership and rejects others with `DATASOURCE_NOT_ALLOWED` before HTTP.

Rejected: deleting the allowlist entirely. Local debugging still needs a way to pin one ID. Rejected: a separate boolean `allowlist-enabled`. An empty list is the off switch; operators already know the property.

Visibility stays with web-sql: missing or unauthorized sources map to existing `DATASOURCE_NOT_FOUND`. Database, Authorization, and Guard checks are unchanged.

### 2. Change the Database prompt in place; do not add `mode` yet

There is no separate "ask the data" product entry besides this Agent. Adding `mode=sql_copilot` would grow `AgentRequest` before the editor needs it. When a second product needs result-in-chat answers, add `mode` then. v1 Copilot and the current Database Agent share one prompt: SQL fences plus optional verification.

Rejected: turning off `executeQuery`. Verification and repair are the accuracy source. The prompt must say to use results only to confirm the statement, not to paste rows into `content`.

### 3. Assert prompt shape with a resource test, not a live model

A unit test loads `database-agent-system-prompt.txt` (and/or the prompt `SpringAiAgentService` attaches) and asserts the fence, no-grid, full-repair, and read-only-verify rules. Existing scripted `DatabaseAgentMultiStepTest` already covers tool order and repair; it does not need a real LLM.

### 4. Docs treat Token as authorization

`docs/local-web-sql.md` and the smoke script stop requiring `allowed-datasource-ids` on the default start command. The property remains documented as an optional lock. Default `application.yml` stays `[]`, with a comment that empty means unrestricted.

## Risks / Trade-offs

- [Empty allowlist opens every ID the token can see] → Intended. web-sql still enforces product visibility; AI Guard still blocks writes; production JDBC stays forbidden.
- [问数-style answers become SQL-first] → Accepted until a second product needs `mode`. Result grids are still available to the model via tools.
- [Models may still omit fences] → Prompt is authoritative; editor also restates the rule in `message`. A snapshot test catches prompt regressions, not model drift.
- [Operators who relied on fail-closed empty list] → Document the new meaning. A non-empty list still locks IDs.

## Migration Plan

Default config does not change its YAML value (`[]`); only the meaning changes. Deploy with the usual AI jar. Rollback is a revert of `allows()` plus the prompt file. Operators who want the old lock must set explicit IDs.

## Open Questions

None for this change. `mode`, memory, and SSE stay deferred as in the editor Copilot plan.
