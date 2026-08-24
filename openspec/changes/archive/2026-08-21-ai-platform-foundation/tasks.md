## 1. Maven Project Foundation

- [x] 1.1 Verify and pin a mutually compatible stable Java 17+, Spring Boot 4.0.x, and Spring AI 2.0.x baseline, including the official property names used by the selected Spring AI release.
- [x] 1.2 Create the root Maven aggregator and `ai-core`, `ai-agent`, `ai-datasource`, and `ai-server` child POMs with centralized dependency/plugin management and one-way module dependencies.
- [x] 1.3 Keep `ai-agent` and `ai-datasource` as buildable empty placeholder modules with no Agent, datasource, SQL, tool, RAG, or MCP implementation.
- [x] 1.4 Add repository ignore rules for IDE/build output and local environment or secret-bearing files.

## 2. Core Chat Capability

- [x] 2.1 Add `ChatRequest`, `ChatResponse`, and the provider-neutral `AiChatService` contract under `com.zorth.aiplatform.core.chat`, including null, blank, and 10,000-character validation constraints on the message.
- [x] 2.2 Add the minimal `AiException` type under `com.zorth.aiplatform.core.exception` without creating speculative exception hierarchies.
- [x] 2.3 Implement plain-class `SpringAiChatService` using a provided Spring AI `ChatClient`, mapping generated text to `ChatResponse` and translating model failures to `AiException`.
- [x] 2.4 Add safe chat/model-call logging for start, success, failure, message length, and elapsed duration without logging prompt content, response content, credentials, or sensitive configuration.

## 3. Server and Provider Wiring

- [x] 3.1 Create the `ai-server` Spring Boot application with Web, Validation, Actuator, `ai-core`, and the official OpenAI-compatible Spring AI starter dependencies.
- [x] 3.2 Add `AiConfiguration` to build a singleton `ChatClient` from the auto-configured builder and explicitly register `SpringAiChatService` as the `AiChatService` bean.
- [x] 3.3 Add `application.yml` configuration for application name, externally supplied API key/base URL/model, temperature `0.2`, and health endpoint exposure using only placeholders or non-sensitive defaults.
- [x] 3.4 Verify the provider integration remains confined to `ai-server` and that no controller or public core contract references a provider-specific model type.

## 4. HTTP and Operational Behavior

- [x] 4.1 Implement `AiChatController` with validated `POST /api/v1/ai/chat` JSON input, direct `ChatResponse` JSON output, and `AiChatService` as its only model-facing dependency.
- [x] 4.2 Add a minimal error response record and `GlobalExceptionHandler` mappings for `INVALID_REQUEST`, `AI_SERVICE_ERROR`, and `INTERNAL_ERROR`, ensuring client responses never expose stack traces or provider details.
- [x] 4.3 Expose `/actuator/health` and confirm its health evaluation performs no `ChatClient` or `ChatModel` invocation.

## 5. Automated Verification

- [x] 5.1 Add `SpringAiChatService` unit tests with a mocked model boundary to verify generated-content mapping, failure translation, and no real network/model call.
- [x] 5.2 Add controller tests for HTTP 200 and the unwrapped content response, plus null, blank, and oversized message rejection with HTTP 400 and no service invocation.
- [x] 5.3 Add exception-handler tests verifying sanitized `AI_SERVICE_ERROR` and `INTERNAL_ERROR` responses without internal exception details.
- [x] 5.4 Add an application-context and health test using test-only dummy or mocked provider configuration, proving startup and health checks require no real API key and make no model call.
- [x] 5.5 Run the complete Maven reactor with `mvn clean test` and resolve all compilation, dependency-direction, context, and test failures.

## 6. Documentation and Acceptance

- [x] 6.1 Rewrite the root README as operator-facing documentation covering project purpose, current chat capability, explicitly unimplemented roadmap, Java/Maven prerequisites, environment variables, startup, chat curl example, health check, and test command.
- [x] 6.2 Document an opt-in real-provider smoke test that uses externally supplied `AI_API_KEY`, `AI_BASE_URL`, and `AI_MODEL` and is never part of the default Maven test lifecycle.
- [x] 6.3 Review the finished tree against the change specs and original definition of done, confirming no Database Agent, datasource, SQL, tool calling, RAG, MCP, memory, streaming, persistence, authorization, or audit-storage work was introduced.
