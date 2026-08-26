## MODIFIED Requirements

### Requirement: Server-controlled agent execution context
The system SHALL copy `conversationId`, `datasourceId`, and `database` from `AgentRequest` into `ToolContext`. When Authorization is present and auth-context succeeds, the system SHALL copy the resolved `userId` into `ToolContext` and MUST NOT use `AgentRequest.userId` for ToolContext or ownership. The model MUST NOT be asked to generate these values. Existing `{ "message": "..." }` agent requests MUST continue to work.

#### Scenario: Context reaches tools
- **WHEN** an authenticated agent request includes `datasourceId`, `database`, and `conversationId`
- **THEN** database tools read those values plus the auth-context `userId` from `ToolContext`

#### Scenario: Message-only agent request remains valid
- **WHEN** a client posts `{ "message": "今天是几号？" }`
- **THEN** the agent endpoint accepts the request, does not register Database Tools, and returns `{ "content": "..." }`

#### Scenario: Request body userId is not copied into ToolContext
- **WHEN** an authenticated agent request body contains `userId` "spoof" and auth-context returns `userId` "1001"
- **THEN** `ToolContext` `userId` is "1001"

## ADDED Requirements

### Requirement: Agent conversation memory
When an Agent request has a resolved `userId` and a conversation id, the system SHALL include prior persisted user and assistant turns for that user-owned conversation in the model prompt, subject to `ai.chat.memory-max-messages`. Memory MUST NOT be registered as a default advisor on the shared `ChatClient`. Agent memory MUST use a namespace distinct from `/api/v1/ai/chat` so the same raw id cannot share a window. The this-turn `message` MAY include editor context that is not persisted; that context MUST NOT replace stored prior turns.

#### Scenario: Follow-up includes the previous visible turn
- **WHEN** an authenticated client completes a turn with `userText` "列出订单" and then sends `userText` "加上时间过滤" with the returned `conversationId`
- **THEN** the model prompt includes the stored user text "列出订单" and the stored assistant reply from the first turn

#### Scenario: Chat and Agent memory stay isolated
- **WHEN** a chat client and an agent client both use conversation id `conv-1` for different users or products
- **THEN** neither product's stored turns appear in the other's model prompt

#### Scenario: Shared ChatClient users are unaffected
- **WHEN** semantic extraction invokes the shared `ChatClient`
- **THEN** that call does not read or write Agent conversation memory

### Requirement: Conversation id generation and ownership on Agent calls
Missing or blank `conversationId` on an authenticated Agent request SHALL cause the service to generate a new identifier, create the conversation for the resolved user, and return the identifier. An authenticated request whose `conversationId` belongs to another user MUST NOT read or write that conversation and MUST fail with HTTP 404 and code `CONVERSATION_NOT_FOUND` (sync) or a client-safe `error` event with that code (stream) without attaching the other user's memory. An unknown id MAY be adopted as a new conversation for the current user.

#### Scenario: Blank conversation id is created
- **WHEN** an authenticated client omits `conversationId`
- **THEN** the JSON body or stream `start` event includes a new `conversationId` and a conversation row exists for that user

#### Scenario: Other user's conversation id is rejected
- **WHEN** an authenticated user B posts an Agent request with user A's `conversationId`
- **THEN** the system does not append to A's messages and responds with 404 `CONVERSATION_NOT_FOUND` or a stream `error` with that code

### Requirement: Optional userText on Agent requests
`AgentRequest` MAY include `userText` of at most 10,000 characters. Blank `userText` SHALL be treated as absent. When present, it is the user-visible turn for memory and history; `message` remains the this-turn model input and MAY include editor context. Omitting `userText` remains valid.

#### Scenario: Request with only message remains valid
- **WHEN** a client posts `{ "message": "列出订单", "datasourceId": "ds-1", "database": "orders" }`
- **THEN** the agent endpoint accepts the request

#### Scenario: Oversized userText is rejected
- **WHEN** a client posts `userText` longer than 10,000 characters
- **THEN** the endpoint responds with HTTP 400 and code `INVALID_REQUEST` and does not invoke the model
