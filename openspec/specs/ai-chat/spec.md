# AI Chat

## Purpose

Define the provider-neutral synchronous chat service, HTTP API, input validation, and minimal response contracts.

## Requirements

### Requirement: Provider-neutral chat service contract
The system SHALL expose an internal `AiChatService` contract under the `com.zorth.aiplatform` package namespace that accepts a request containing only a message and returns a response containing only generated content. The contract SHALL NOT expose provider-specific or Spring AI types.

#### Scenario: Core chat request succeeds
- **WHEN** a valid message is submitted to `AiChatService` and the configured model returns text
- **THEN** the service returns a `ChatResponse` whose content equals the returned model text

#### Scenario: Provider implementation can change behind the contract
- **WHEN** the configured `ChatModel` implementation is replaced
- **THEN** the `AiChatService` contract and its callers require no source-level change

### Requirement: Synchronous chat endpoint
The system SHALL expose `POST /api/v1/ai/chat`, consume `application/json`, and return an unwrapped JSON chat response on success. The controller SHALL invoke `AiChatService` and SHALL NOT directly depend on `ChatClient`, `ChatModel`, or a provider-specific model class.

#### Scenario: Valid HTTP chat request
- **WHEN** a client posts `{ "message": "你好" }` and the chat service returns `你好，有什么可以帮助你？`
- **THEN** the endpoint responds with HTTP 200 and `{ "content": "你好，有什么可以帮助你？" }`

### Requirement: Chat input validation
The system MUST reject a message that is null, blank, or longer than 10,000 characters before invoking `AiChatService`.

#### Scenario: Null message is rejected
- **WHEN** a client posts a JSON request with a null message
- **THEN** the endpoint responds with HTTP 400 and error code `INVALID_REQUEST`, and does not invoke `AiChatService`

#### Scenario: Blank message is rejected
- **WHEN** a client posts a JSON request whose message contains no non-whitespace characters
- **THEN** the endpoint responds with HTTP 400 and error code `INVALID_REQUEST`, and does not invoke `AiChatService`

#### Scenario: Oversized message is rejected
- **WHEN** a client posts a JSON request whose message is longer than 10,000 characters
- **THEN** the endpoint responds with HTTP 400 and error code `INVALID_REQUEST`, and does not invoke `AiChatService`

### Requirement: Minimal response contracts
The system SHALL return successful chat results as `{ "content": "..." }` without a generic response wrapper and SHALL return errors as a JSON object containing stable `code` and safe `message` fields.

#### Scenario: Success is not wrapped
- **WHEN** the chat endpoint completes successfully
- **THEN** the response body contains `content` at the top level and does not require a `data` envelope

#### Scenario: Invalid request has a stable error shape
- **WHEN** chat request validation fails
- **THEN** the response body contains error code `INVALID_REQUEST` and a client-safe message
