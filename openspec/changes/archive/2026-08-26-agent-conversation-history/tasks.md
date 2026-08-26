## 1. Metadata store and schema

- [x] 1.1 Add JDBC + Flyway to `ai-server` with env-backed datasource defaulting locally to MySQL database `aiplatform`; tests use an embedded database
- [x] 1.2 Flyway migration: `agent_conversation` (`id`, `user_id`, `title`, `datasource_id`, `database_name`, `created_at`, `updated_at`) with index `(user_id, updated_at, id)` and `agent_message` (`id`, `conversation_id`, `role`, `content`, `tools_json`, `created_at`)
- [x] 1.3 Conversation repository: insert/get/list-by-user/delete-owned; missing or other-user id returns empty/not found, never another user's row

## 2. Auth-context user resolution

- [x] 2.1 Add `ai.auth.context-url`, `internal-service-key`, connect/read timeout, and a small TTL cache matching the SQL editor contract
- [x] 2.2 Resolve `userId` from Bearer + auth-context; on Agent calls copy that id into `ToolContext` and ignore body `userId` for ownership and ToolContext
- [x] 2.3 Tests: spoofed body `userId` does not win; missing Authorization on `/agent` still does not 401; conversation APIs without a token return 401 `UNAUTHENTICATED`; auth-context down on conversation APIs returns 503 `AUTH_SERVICE_UNAVAILABLE`

## 3. Persist turns and Agent memory

- [x] 3.1 Add optional `userText` on `AgentRequest` (max 10,000; blank → null) and reject oversized `userText` with 400 `INVALID_REQUEST`
- [x] 3.2 On authenticated Agent success, persist user turn (`userText` or `message`) plus assistant `content`; do not persist editor-only `message` preamble when `userText` is present; do not persist on error/abort; optionally store tool name/status on the assistant row
- [x] 3.3 Load last `ai.chat.memory-max-messages` stored turns into the Agent prompt under namespace `agent:{userId}:{conversationId}`; do not register memory as a default `ChatClient` advisor; do not share Chat's in-memory store
- [x] 3.4 Generate conversation id when missing; adopt unknown ids for the current user; other-user id on Agent sync returns 404 `CONVERSATION_NOT_FOUND` and on stream emits `error` with that code; anonymous Agent writes no rows and loads no memory
- [x] 3.5 Tests with a scripted `ChatClient`: follow-up prompt contains prior `userText` not the fat `message`; Chat id `conv-1` does not leak into Agent; semantic/`ChatClient` users still have no Agent memory; `userText` vs `message` storage; cross-user 404

## 4. Conversation HTTP APIs

- [x] 4.1 `GET /api/v1/ai/agent/conversations` — current user, `updatedAt` desc, max 50, fields `id`, `title`, `datasourceId`, `database`, `updatedAt`
- [x] 4.2 `GET /api/v1/ai/agent/conversations/{id}` — metadata + chronological messages; other-user or missing → 404 `CONVERSATION_NOT_FOUND`
- [x] 4.3 `DELETE /api/v1/ai/agent/conversations/{id}` — 204 for owner; 404 otherwise; deleted turns are not loaded on a later Agent call
- [x] 4.4 Title from first stored user text clipped to 80 characters; successful Agent turns update last `datasourceId`/`database` when present
- [x] 4.5 Controller tests covering list isolation, detail/delete 404, 401 without token, and unwrapped JSON / existing error shape

## 5. Docs and verification

- [x] 5.1 Document `aiplatform` datasource, auth-context env, conversation APIs, `userText`, and that body `userId` is not authoritative (`README.md`, `docs/local-web-sql.md`)
- [x] 5.2 Run `ai-server` / `ai-agent` unit and controller tests (no live model) and fix regressions; `/api/v1/ai/chat` and message-only Agent remain unchanged
