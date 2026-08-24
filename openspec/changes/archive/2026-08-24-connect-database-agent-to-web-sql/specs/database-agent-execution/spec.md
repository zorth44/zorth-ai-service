## ADDED Requirements

### Requirement: Authorization is forwarded in memory
When an agent request includes `datasourceId`, the system SHALL copy the inbound `Authorization` header into `ToolContext` and MUST NOT put it on `AgentRequest`, in tool schemas, or in audit/application logs. Message-only agent requests and `/api/v1/ai/chat` MUST remain usable without that header. Missing Authorization MUST NOT fail the HTTP `/api/v1/ai/agent` call with 401 or 500; database tools return `AUTH_ERROR` instead.

#### Scenario: Token reaches tools without entering the request DTO
- **WHEN** a client posts a database agent request with `Authorization: Bearer secret-token`
- **THEN** web-sql HTTP calls use that value and `AgentRequest` has no authorization field

#### Scenario: Message-only agent stays anonymous
- **WHEN** a client posts `{ "message": "今天是几号？" }` without `Authorization`
- **THEN** the agent endpoint accepts the request and does not register a requirement for that header

### Requirement: Result values are not for model-side arithmetic
When `datasourceId` is present, Database Agent guidance MUST tell the model that numeric result cells may be strings and that aggregation belongs in SQL, not in the final answer arithmetic.

#### Scenario: Prompt warns about string numbers
- **WHEN** the agent executes a request that includes `datasourceId`
- **THEN** the system prompt states that result numbers may be strings and must not be recalculated in the answer

## MODIFIED Requirements

### Requirement: Server-controlled agent execution context
The system SHALL copy `conversationId`, `userId`, `datasourceId`, and `database` from `AgentRequest` into `ToolContext`. The model MUST NOT be asked to generate these values. Existing `{ "message": "..." }` agent requests MUST continue to work.

#### Scenario: Context reaches tools
- **WHEN** an agent request includes `datasourceId`, `database`, `conversationId`, and `userId`
- **THEN** database tools read those exact values from `ToolContext`

#### Scenario: Message-only agent request remains valid
- **WHEN** a client posts `{ "message": "今天是几号？" }`
- **THEN** the agent endpoint accepts the request, does not register Database Tools, and returns `{ "content": "..." }`

### Requirement: Tool and SQL audit logs
The system SHALL log `conversationId`, `userId`, `datasourceId`, `database`, `requestId`, tool name, tool arguments needed for audit (including SQL), result status, duration, query row count when applicable, `executionId` when `executeQuery` sent one, and error message. Query result rows MUST NOT be persisted in this phase. Authorization tokens and other credentials MUST NOT appear in the audit line.

#### Scenario: executeQuery is auditable
- **WHEN** `executeQuery` runs
- **THEN** server logs record who issued the request, which conversation, datasource, and database were used, the SQL, the `executionId`, success or failure, and row count when available

#### Scenario: Credentials are absent from audit
- **WHEN** `ToolContext` contains an Authorization Bearer value and `executeQuery` is audited
- **THEN** the audit message does not contain that Bearer value
