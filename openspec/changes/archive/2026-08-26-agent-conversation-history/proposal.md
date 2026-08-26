## Why

SQL editor Copilot already sends `conversationId` on every Agent turn, but `/api/v1/ai/agent` never reads Chat Memory: each request is a single user message, so follow-ups that do not re-insert SQL fail. There is also no durable, user-scoped conversation store, so a refresh or another instance loses the thread, and the request-body `userId` cannot isolate users.

## What Changes

- Resolve the authenticated `userId` from the inbound Bearer token via the same auth-context contract the SQL editor uses. ToolContext `userId` comes from that resolution, not from the request body. Body `userId` is ignored for ownership.
- When Agent requests include a conversation identifier, opt in to Chat Memory for that call only (not as a default `ChatClient` advisor). Agent memory is namespaced separately from `/api/v1/ai/chat`. Missing `conversationId` generates a new id and returns it on `start` / `completed` / the JSON response.
- Persist Agent conversations and user-visible messages keyed by resolved `userId`. Subsequent turns with the same id include prior user/assistant turns in the model prompt, subject to the existing memory window.
- Expose current-user conversation APIs: list, get messages, delete. A conversation that is missing or owned by another user is indistinguishable (404).
- Store the user-visible turn text in memory and history. Per-turn editor context stays on `message` for this call only and MUST NOT accumulate in the memory window.
- **Not in this change:** `/api/v1/ai/chat` persistence, `mode=sql_copilot`, new tools, longer `message` limits, SQL service contract changes, or trusting client-supplied `userId`. Message-only Agent without Authorization keeps working and does not persist history.

## Capabilities

### New Capabilities

- `agent-conversation-history`: Durable Agent conversations owned by the authenticated user, including list/get/delete APIs, message persistence, and isolation so one user cannot read or continue another user's thread.

### Modified Capabilities

- `database-agent-execution`: Agent calls with a conversation id opt in to namespaced Chat Memory; `userId` in ToolContext is resolved from the Bearer token; a missing conversation id is generated and returned; optional `userText` is the visible turn stored in memory.

## Impact

- `ai-agent`: `SpringAiAgentService` attaches memory per call, generates conversation ids, writes completed turns.
- `ai-server`: auth-context client, conversation REST endpoints, Flyway schema, JDBC configuration. First persistent store in this service.
- `AgentRequest`: optional `userText`; `userId` remains accepted but is not authoritative.
- New env: auth context URL / internal key, metadata datasource (local MySQL database `aiplatform`, tests use an embedded store).
- Tests: no live model. Scripted ChatClient plus repository tests for isolation, 404, and memory window content.
- Sibling editor change `copilot-conversation-history` consumes these APIs; this change does not modify the SQL editor.
