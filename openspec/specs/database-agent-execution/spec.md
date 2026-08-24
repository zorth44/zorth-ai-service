# Database Agent Execution

## Purpose

Define how Database Agent requests carry server-controlled context, attach tools and prompts, run native multi-step tool calling, keep the chat API compatible, and record SQL audit logs.

## Requirements

### Requirement: Server-controlled agent execution context
The system SHALL copy `conversationId`, `userId`, and `datasourceId` from `AgentRequest` into `ToolContext`. The model MUST NOT be asked to generate these values. Existing `{ "message": "..." }` agent requests MUST continue to work.

#### Scenario: Context reaches tools
- **WHEN** an agent request includes `datasourceId`, `conversationId`, and `userId`
- **THEN** database tools read those exact values from `ToolContext`

#### Scenario: Message-only agent request remains valid
- **WHEN** a client posts `{ "message": "今天是几号？" }`
- **THEN** the agent endpoint accepts the request, does not register Database Tools, and returns `{ "content": "..." }`

### Requirement: Database system prompt without a forced sequence
When `datasourceId` is present, the system SHALL add Database Agent guidance: discover tables if needed, inspect relevant schema, generate read-only SQL, validate, execute, answer from results, and repair failed SQL. The prompt MUST NOT require every question to call every tool.

#### Scenario: Guidance is attached for database requests
- **WHEN** the agent executes a request that includes `datasourceId`
- **THEN** the system prompt includes read-only Database Agent instructions

#### Scenario: Foundation requests keep the original prompt
- **WHEN** the agent executes a request without `datasourceId`
- **THEN** Database Tools are not registered and the original foundation prompt is used

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

### Requirement: Tool and SQL audit logs
The system SHALL log `conversationId`, `userId`, `datasourceId`, tool name, tool arguments needed for audit (including SQL), result status, duration, query row count when applicable, and error message. Query result rows MUST NOT be persisted in this phase.

#### Scenario: executeQuery is auditable
- **WHEN** `executeQuery` runs
- **THEN** server logs record who issued the request, which conversation and datasource were used, the SQL, success or failure, and row count when available
