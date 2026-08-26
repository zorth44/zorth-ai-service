# AI Chat

## Purpose

Define the provider-neutral chat service, HTTP APIs, input validation, streaming, in-memory multi-turn memory, and minimal response contracts.

## Requirements

### Requirement: Provider-neutral chat service contract
The system SHALL expose an internal `AiChatService` contract under the `com.zorth.aiplatform` package namespace that accepts a request containing a message and an optional conversation identifier, and returns generated content together with the conversation identifier used for the turn. The contract SHALL also expose a streaming operation that emits start, delta, completed, and error events. The contract SHALL NOT expose provider-specific types.

#### Scenario: Core chat request succeeds
- **WHEN** a valid message is submitted to `AiChatService` and the configured model returns text
- **THEN** the service returns a `ChatResponse` whose content equals the returned model text and whose `conversationId` identifies the turn

#### Scenario: Provider implementation can change behind the contract
- **WHEN** the configured `ChatModel` implementation is replaced
- **THEN** the `AiChatService` contract and its callers require no source-level change

### Requirement: Synchronous chat endpoint
The system SHALL expose `POST /api/v1/ai/chat`, consume `application/json`, and return an unwrapped JSON chat response on success. The controller SHALL invoke `AiChatService` and SHALL NOT directly depend on `ChatClient`, `ChatModel`, or a provider-specific model class.

#### Scenario: Valid HTTP chat request
- **WHEN** a client posts `{ "message": "你好" }` and the chat service returns content `你好，有什么可以帮助你？` with a conversation id
- **THEN** the endpoint responds with HTTP 200 and a JSON body containing `content` and `conversationId` at the top level

### Requirement: Streaming chat endpoint
The system SHALL expose `POST /api/v1/ai/chat/stream`, consume `application/json`, and produce `text/event-stream`. The stream SHALL emit a `start` event with `conversationId`, zero or more `delta` events with `content`, and a `completed` event with `conversationId`. Model or platform failures after the stream has started SHALL emit an `error` event with code `AI_SERVICE_ERROR` and a client-safe message, without provider SDK details.

#### Scenario: Valid streaming chat request
- **WHEN** a client posts a valid chat request to `/api/v1/ai/chat/stream` and the model yields tokens
- **THEN** the endpoint responds with SSE events `start`, one or more `delta`, and `completed`

#### Scenario: Streaming model failure is sanitized
- **WHEN** the model fails after a stream has started
- **THEN** the client receives an `error` event whose payload does not contain provider exception details

### Requirement: In-memory conversation memory
The system SHALL attach Spring AI `MessageChatMemoryAdvisor` only to chat calls, backed by a `ChatMemory` that uses `ChatMemoryRepository`. The default repository SHALL be `InMemoryChatMemoryRepository`. Chat memory MUST NOT be registered as a default advisor on the shared `ChatClient`. Missing or blank `conversationId` values SHALL cause the service to generate a new identifier, persist the turn under that identifier, and return it. Subsequent chat or stream requests with the same identifier MUST include prior turns in the model prompt, subject to the configured message window.

#### Scenario: Conversation continues across requests
- **WHEN** a client sends a follow-up message with a previously returned `conversationId`
- **THEN** the model prompt includes earlier user and assistant messages from that conversation

#### Scenario: Conversations are isolated
- **WHEN** two clients use different `conversationId` values
- **THEN** neither conversation's history is included in the other's model prompt

#### Scenario: Streaming writes the completed turn into memory
- **WHEN** a streaming request completes successfully
- **THEN** the user message and aggregated assistant reply are stored under the same `conversationId` used by synchronous chat

#### Scenario: Shared ChatClient users are unaffected
- **WHEN** agent or semantic extraction invokes the shared `ChatClient`
- **THEN** those calls do not read or write chat conversation memory unless they explicitly opt in

### Requirement: Chat input validation
The system MUST reject a message that is null, blank, or longer than 10,000 characters before invoking `AiChatService`. The system MUST reject a `conversationId` longer than 128 characters before invoking `AiChatService`.

#### Scenario: Null message is rejected
- **WHEN** a client posts a JSON request with a null message
- **THEN** the endpoint responds with HTTP 400 and error code `INVALID_REQUEST`, and does not invoke `AiChatService`

#### Scenario: Blank message is rejected
- **WHEN** a client posts a JSON request whose message contains no non-whitespace characters
- **THEN** the endpoint responds with HTTP 400 and error code `INVALID_REQUEST`, and does not invoke `AiChatService`

#### Scenario: Oversized message is rejected
- **WHEN** a client posts a JSON request whose message is longer than 10,000 characters
- **THEN** the endpoint responds with HTTP 400 and error code `INVALID_REQUEST`, and does not invoke `AiChatService`

#### Scenario: Oversized conversation id is rejected
- **WHEN** a client posts a JSON request whose `conversationId` is longer than 128 characters
- **THEN** the endpoint responds with HTTP 400 and error code `INVALID_REQUEST`, and does not invoke `AiChatService`

### Requirement: Minimal response contracts
The system SHALL return successful chat results as `{ "content": "...", "conversationId": "..." }` without a generic response wrapper and SHALL return errors as a JSON object containing stable `code` and safe `message` fields. A request that contains only `message` remains valid.

#### Scenario: Success is not wrapped
- **WHEN** the chat endpoint completes successfully
- **THEN** the response body contains `content` at the top level and does not require a `data` envelope

#### Scenario: Invalid request has a stable error shape
- **WHEN** chat request validation fails
- **THEN** the response body contains error code `INVALID_REQUEST` and a client-safe message
