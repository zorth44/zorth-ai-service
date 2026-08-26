## Context

`POST /api/v1/ai/chat` already opts into Spring AI `MessageChatMemoryAdvisor` with an in-process `InMemoryChatMemoryRepository`. `POST /api/v1/ai/agent` copies `conversationId` and request-body `userId` into `ToolContext` for audit only. Each Agent call sends a single `.user(request.message())` with no prior turns.

The SQL editor Copilot keys threads by tab id, stuffs current editor SQL into `message`, and never persists. Sibling change `copilot-conversation-history` will list and resume threads from this service. AI Platform still has no metadata database and does not call auth-context; it only forwards the Bearer token to web-sql tools.

Existing constraint that must stay: missing `Authorization` MUST NOT 401 `/api/v1/ai/agent`. Conversation CRUD is a new, authenticated surface.

## Goals / Non-Goals

**Goals:**

- Resolve `userId` from Bearer + auth-context; never trust body `userId` for ownership.
- Multi-turn Agent memory namespaced away from Chat.
- Durable per-user conversation list and messages; 404 for missing or other-user ids.
- Persist the visible user turn (`userText` if present, else `message`). Keep per-turn editor context off the memory window.
- Message-only Agent without a token keeps working and does not write history.

**Non-Goals:**

- Durable `/api/v1/ai/chat` history.
- `mode=sql_copilot`, new tools, raising the 10,000-character `message` cap.
- SQL service schema or APIs.
- Sharing one ChatMemory bean between Chat and Agent.

## Decisions

### 1. Conversations live in AI Platform, not the SQL editor

The model window and the history the user reopens are the same rows. The editor is a client: Bearer in, list/get/delete/stream out.

Rejected: storing bubbles in `zorth-web-sql-editor` and hoping Agent memory stays in sync. Two sources of truth, and the 10k `message` cap cannot carry a real transcript.

### 2. `userId` from auth-context, same contract as the SQL service

New `ai.auth.context-url` and `ai.auth.internal-service-key`. On Agent and conversation APIs, call `GET {context-url}` with the inbound Bearer and `X-Internal-Service-Key`. Cache like the SQL service (TTL + max size).

| Call | No/invalid token | Auth-context down |
| --- | --- | --- |
| `GET/DELETE /api/v1/ai/agent/conversations…` | 401 `UNAUTHENTICATED` | 503 `AUTH_SERVICE_UNAVAILABLE` |
| `POST /api/v1/ai/agent` and `/stream` | Existing behavior: no 401; no persist; no user-scoped memory; tools still return `AUTH_ERROR` when they need a token | Same: do not persist; do not use body `userId` |

Rejected: trusting `AgentRequest.userId`. Anyone can impersonate. Body `userId` remains accepted for compatibility and is ignored for ownership. ToolContext `userId` is the resolved id when present.

### 3. Memory key `agent:{userId}:{conversationId}`

Chat keeps `conversationId` as today. Agent prefixes so the two products cannot share a window even if a client reuses an id.

On an authenticated Agent call:

1. Blank `conversationId` → generate UUID, create a row for this user, return it on `start` / JSON.
2. Id exists for this user → continue.
3. Id exists for another user → 404 `CONVERSATION_NOT_FOUND`; do not read or write that row.
4. Id unknown → create a row for this user with that id (client may mint a UUID).

Anonymous Agent: generate an id for the response if missing; do not persist; do not load memory.

### 4. Custom turn store, not auto-saving `MessageChatMemoryAdvisor` on `.user(message)`

`message` is the fat editor prompt. If the advisor saved that, the window would fill with duplicate SQL.

Prompt construction:

1. Load the last `ai.chat.memory-max-messages` **stored** user/assistant turns for this conversation (default 20).
2. System prompt as today (foundation ± Database).
3. Prior stored turns as chat history.
4. This-turn editor context from `message` as a **non-persisted** extra (additional system or a throwaway user preamble).
5. This-turn visible text: `userText` if non-blank, else `message`.

On successful completion (sync or stream `completed`), append one user row (`userText` or `message`) and one assistant row (aggregated `content`). Tool names/statuses may be stored on the assistant row for UI replay; tool arguments and result payloads MUST NOT be stored.

Abort, `error` event, or thrown failure MUST NOT append a turn. Do not register memory as a default `ChatClient` advisor.

Rejected: replacing Chat's `InMemoryChatMemoryRepository` with JDBC in this change. Chat stays in-process.

### 5. First metadata database: Flyway + JDBC, MySQL locally

Local topology already has MySQL. Use a separate database `aiplatform` (not `sqleditor`). Tests use an embedded JDBC database. Production URL/user/password via env.

Tables:

- `agent_conversation`: `id`, `user_id`, `title`, `datasource_id`, `database_name`, `created_at`, `updated_at`. Index `(user_id, updated_at DESC, id DESC)`.
- `agent_message`: `id`, `conversation_id`, `role` (`user` \| `assistant`), `content`, `tools_json` (nullable), `created_at`.

Title: first persisted `userText`/`message`, clipped to 80 characters, set once unless empty.

Rejected: SQLite file in the AI jar (ops already run MySQL). Rejected: Spring AI `JdbcChatMemoryRepository` alone (no per-user list).

### 6. Conversation HTTP contract

Prefix `/api/v1/ai/agent/conversations`. Unwrapped JSON, same error shape as chat (`code`, `message`). Cursor pagination like SQL history if the list can grow; v1 MAY return a single page of the most recent 50.

```http
GET    /api/v1/ai/agent/conversations
GET    /api/v1/ai/agent/conversations/{id}
DELETE /api/v1/ai/agent/conversations/{id}
```

List item: `id`, `title`, `datasourceId`, `database`, `updatedAt`.
Detail: list fields plus `messages[]` with `id`, `role`, `content`, `tools` (optional), `createdAt`.
Delete: 204. Missing or other-user: 404 `CONVERSATION_NOT_FOUND` (do not leak existence).
No `Authorization`: 401.

Successful Agent turns update `datasource_id` / `database_name` from the request when those fields are present, so the list can show last connection. Conversations are **not** locked to one datasource; the next turn's tools use the current request.

### 7. Optional `userText` on `AgentRequest`

Max 10,000 characters, same as `message`. Blank → null. Copilot sends the visible sentence here and keeps editor context in `message`. Other Agent clients omit it and history stores `message`.

## Risks / Trade-offs

- [Auth-context is a new runtime dependency] → Same URL/key as SQL editor locally. Conversation APIs fail closed (401/503). Agent generate still works without persist if auth is down, matching today's tool `AUTH_ERROR` path.
- [First DB in AI Platform] → Isolated schema `aiplatform`. Rollback is revert + drop tables. Health remains non-model; add datasource to actuator only if it stays cheap.
- [404 on cross-user `conversationId` during generate] → Editor starts a new thread. Better than silently joining another user.
- [Editor context not in memory] → Follow-ups that never insert SQL still see prior assistant SQL in the window; current editor SQL still arrives this turn via `message`.
- [In-memory Chat unchanged] → Chat still dies on restart. Out of scope.

## Migration Plan

Deploy AI jar with Flyway against an empty `aiplatform` database and auth-context env. Existing Agent clients that send only `message` keep working. Clients that sent body `userId` keep compiling; ownership ignores it.

Rollback: revert the jar. Orphaned rows are unused. No backfill; in-process Copilot bubbles are not migrated.

## Open Questions

None for this change. Conversation-per-tab vs independent threads is an editor decision (independent). Tool payloads stay out of storage.
