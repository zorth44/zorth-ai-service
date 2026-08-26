# Agent Conversation History

## Purpose

Define durable, user-scoped Agent conversations: auth-context ownership, list/get/delete APIs, persisted user-visible turns, and isolation so one user cannot read or continue another user's thread.

## Requirements

### Requirement: Authenticated user owns conversations
The system SHALL persist Agent conversations under the `userId` resolved from the inbound Bearer token via the configured auth-context service. The `userId` field on `AgentRequest` MUST NOT authorize reads, writes, or memory. A conversation row MUST include `user_id`, `id`, `title`, optional last `datasourceId` and `database`, `created_at`, and `updated_at`.

#### Scenario: Token user owns a new conversation
- **WHEN** an authenticated client posts an Agent request without `conversationId`
- **THEN** the system creates a conversation for the auth-context `userId` and returns that conversation id on the JSON response or stream `start`/`completed` events

#### Scenario: Body userId does not take ownership
- **WHEN** an authenticated client posts an Agent request whose body `userId` differs from the auth-context `userId`
- **THEN** the conversation is stored under the auth-context `userId` and is not visible to the body `userId`

### Requirement: Current-user conversation list
The system SHALL expose `GET /api/v1/ai/agent/conversations` requiring `Authorization`. The response SHALL list conversations for the resolved user only, ordered by `updatedAt` descending, at most 50 items, each with `id`, `title`, `datasourceId`, `database`, and `updatedAt`.

#### Scenario: List own conversations
- **WHEN** user A has two conversations and user B has one
- **THEN** A's list contains A's two items and MUST NOT contain B's id

#### Scenario: Unauthenticated list is rejected
- **WHEN** a client calls the list endpoint without a valid Bearer token
- **THEN** the endpoint responds with HTTP 401 and code `UNAUTHENTICATED` and does not return rows

### Requirement: Current-user conversation detail
The system SHALL expose `GET /api/v1/ai/agent/conversations/{id}` requiring `Authorization`. The response SHALL include the conversation metadata and messages (`id`, `role`, `content`, optional `tools`, `createdAt`) in chronological order. A missing id or an id owned by another user SHALL return HTTP 404 and code `CONVERSATION_NOT_FOUND` with no indication which case applied.

#### Scenario: Read own messages
- **WHEN** the owner requests a conversation that has a user turn and an assistant turn
- **THEN** the detail body includes those two messages in order with the stored user-visible content

#### Scenario: Other user's conversation is not found
- **WHEN** user B requests user A's conversation id
- **THEN** the endpoint responds with HTTP 404 and code `CONVERSATION_NOT_FOUND`

### Requirement: Current-user conversation delete
The system SHALL expose `DELETE /api/v1/ai/agent/conversations/{id}` requiring `Authorization`. A successful delete SHALL return HTTP 204 and remove the conversation and its messages. A missing id or an id owned by another user SHALL return HTTP 404 and code `CONVERSATION_NOT_FOUND`. Subsequent Agent calls MUST NOT load deleted turns.

#### Scenario: Owner deletes a conversation
- **WHEN** the owner deletes their conversation
- **THEN** the response is HTTP 204 and a later GET of that id returns 404

#### Scenario: Delete another user's conversation is not found
- **WHEN** user B deletes user A's conversation id
- **THEN** the endpoint responds with HTTP 404, A's conversation remains, and A's later GET still returns the messages

### Requirement: Persisted turns are user-visible content
When an authenticated Agent request completes successfully, the system SHALL append one `user` message and one `assistant` message. The user message content SHALL be `userText` when that field is non-blank, otherwise `message`. The assistant message content SHALL be the aggregated model `content`. Per-turn editor context that appears only in `message` while `userText` is present MUST NOT be stored as additional user messages. Tool arguments, query result rows, Authorization values, and credentials MUST NOT be persisted. Optional tool name/status summaries MAY be stored on the assistant message for UI replay.

#### Scenario: userText is stored instead of the fat prompt
- **WHEN** an authenticated request includes `userText` "加上时间过滤" and a longer `message` that contains editor SQL
- **THEN** the stored user message content is "加上时间过滤" and does not contain the editor SQL block

#### Scenario: Failed or aborted turns are not stored
- **WHEN** an Agent stream emits `error` or the request fails before completion
- **THEN** the system does not append a user/assistant pair for that attempt

### Requirement: Anonymous Agent does not persist history
When the Agent request has no resolved `userId` (missing or unusable Authorization), the system MUST NOT insert conversation or message rows. Message-only `{ "message": "..." }` Agent requests MUST remain valid.

#### Scenario: Message-only request writes no rows
- **WHEN** a client posts `{ "message": "今天是几号？" }` without Authorization
- **THEN** the agent endpoint accepts the request and no conversation row is created
