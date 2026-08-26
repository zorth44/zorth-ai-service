# Database Agent Execution

## Purpose

Define how Database Agent requests carry server-controlled context, attach tools and prompts, run native multi-step tool calling, keep the chat API compatible, and record SQL audit logs.

## Requirements

### Requirement: Server-controlled agent execution context
The system SHALL copy `conversationId`, `userId`, `datasourceId`, and `database` from `AgentRequest` into `ToolContext`. The model MUST NOT be asked to generate these values. Existing `{ "message": "..." }` agent requests MUST continue to work.

#### Scenario: Context reaches tools
- **WHEN** an agent request includes `datasourceId`, `database`, `conversationId`, and `userId`
- **THEN** database tools read those exact values from `ToolContext`

#### Scenario: Message-only agent request remains valid
- **WHEN** a client posts `{ "message": "今天是几号？" }`
- **THEN** the agent endpoint accepts the request, does not register Database Tools, and returns `{ "content": "..." }`

### Requirement: Authorization is forwarded in memory
When an agent request includes `datasourceId`, the system SHALL copy the inbound `Authorization` header into `ToolContext` and MUST NOT put it on `AgentRequest`, in tool schemas, or in audit/application logs. Message-only agent requests and `/api/v1/ai/chat` MUST remain usable without that header. Missing Authorization MUST NOT fail the HTTP `/api/v1/ai/agent` call with 401 or 500; database tools return `AUTH_ERROR` instead.

#### Scenario: Token reaches tools without entering the request DTO
- **WHEN** a client posts a database agent request with `Authorization: Bearer secret-token`
- **THEN** web-sql HTTP calls use that value and `AgentRequest` has no authorization field

#### Scenario: Message-only agent stays anonymous
- **WHEN** a client posts `{ "message": "今天是几号？" }` without `Authorization`
- **THEN** the agent endpoint accepts the request and does not register a requirement for that header

### Requirement: Database system prompt without a forced sequence
When `datasourceId` is present, the system SHALL add Database Agent guidance: discover tables if needed, inspect relevant schema, generate read-only SQL, validate, execute, answer from results, and repair failed SQL. The prompt MUST NOT require every question to call every tool.

#### Scenario: Guidance is attached for database requests
- **WHEN** the agent executes a request that includes `datasourceId`
- **THEN** the system prompt includes read-only Database Agent instructions

#### Scenario: Foundation requests keep the original prompt
- **WHEN** the agent executes a request without `datasourceId`
- **THEN** Database Tools are not registered and the original foundation prompt is used

### Requirement: Result values are not for model-side arithmetic
When `datasourceId` is present, Database Agent guidance MUST tell the model that numeric result cells may be strings and that aggregation belongs in SQL, not in the final answer arithmetic.

#### Scenario: Prompt warns about string numbers
- **WHEN** the agent executes a request that includes `datasourceId`
- **THEN** the system prompt states that result numbers may be strings and must not be recalculated in the answer

### Requirement: Native multi-step tool calling and SQL repair
The system SHALL rely on Spring AI tool calling for successive tool calls in one request. Application code MUST NOT implement a tool-calling `while` loop. A failed `executeQuery` MUST remain inside the same request so the model can inspect schema and retry.

#### Scenario: Discovery then query
- **WHEN** a scripted model calls `listTables`, then `getTableSchema`, then `checkSql`, then `executeQuery`
- **THEN** Spring AI executes each tool and returns the final answer without application-authored looping

#### Scenario: Failed SQL can be repaired
- **WHEN** the first `executeQuery` returns an unknown-column error
- **THEN** the same request can continue with `getTableSchema`, a corrected `checkSql`, and a second `executeQuery`

### Requirement: Chat API compatibility
`POST /api/v1/ai/chat` MUST keep accepting `{ "message": "..." }` and returning `{ "content": "..." }`. This change MUST NOT require clients to send `datasourceId` to the chat endpoint.

#### Scenario: Existing chat request still works
- **WHEN** a client posts `{ "message": "你好" }` to `/api/v1/ai/chat`
- **THEN** the endpoint behaves as before and does not invoke database tools

### Requirement: Streaming agent endpoint
The system SHALL expose `POST /api/v1/ai/agent/stream`, consume `application/json`, and produce `text/event-stream`. The stream SHALL emit a `start` event, zero or more `delta` events with `content`, zero or more `tool` events with `toolName` and `status`, and a `completed` event. Tool events MUST NOT include tool arguments, results, or credentials. Model or platform failures after the stream has started SHALL emit an `error` event with code `AI_SERVICE_ERROR` and a client-safe message. The existing synchronous `POST /api/v1/ai/agent` SHALL remain available.

#### Scenario: Valid streaming agent request
- **WHEN** a client posts a valid agent request to `/api/v1/ai/agent/stream` and the model yields tokens
- **THEN** the endpoint responds with SSE events `start`, one or more `delta`, and `completed`

#### Scenario: Tool progress is visible without leaking payloads
- **WHEN** the model executes `listTables` during a streaming agent request
- **THEN** the stream emits `tool` events with `toolName=listTables` and `STARTED` then `SUCCESS` or `FAILURE`, and the payload does not contain table names or SQL

#### Scenario: Streaming model failure is sanitized
- **WHEN** the model fails after an agent stream has started
- **THEN** the client receives an `error` event whose payload does not contain provider exception details

### Requirement: Tool and SQL audit logs
The system SHALL log `conversationId`, `userId`, `datasourceId`, `database`, `requestId`, tool name, tool arguments needed for audit (including SQL), result status, duration, query row count when applicable, `executionId` when `executeQuery` sent one, and error message. Query result rows MUST NOT be persisted in this phase. Authorization tokens and other credentials MUST NOT appear in the audit line.

#### Scenario: executeQuery is auditable
- **WHEN** `executeQuery` runs
- **THEN** server logs record who issued the request, which conversation, datasource, and database were used, the SQL, the `executionId`, success or failure, and row count when available

#### Scenario: Credentials are absent from audit
- **WHEN** `ToolContext` contains an Authorization Bearer value and `executeQuery` is audited
- **THEN** the audit message does not contain that Bearer value
