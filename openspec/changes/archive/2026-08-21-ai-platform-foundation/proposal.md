## Why

The repository currently contains requirements but no executable application foundation. Before building Database Agent, Text-to-SQL, tool calling, or retrieval features, the project needs a minimal, provider-neutral, testable Spring Boot and Spring AI baseline that proves model-backed chat can be exposed safely through a stable platform API.

## What Changes

- Establish a Java 17+ Maven multi-module project with `ai-core`, `ai-agent`, `ai-datasource`, and `ai-server` modules, while keeping the agent and datasource modules intentionally empty in this change.
- Introduce a provider-neutral `AiChatService` contract and minimal chat request/response types under the `com.zorth.aiplatform` namespace.
- Integrate one configurable OpenAI-compatible chat model through the official Spring AI starter, `ChatModel`, and a Spring-managed `ChatClient`.
- Expose `POST /api/v1/ai/chat` with request validation and a minimal JSON response.
- Add safe error handling, operational logging, and an Actuator health endpoint without invoking a paid model during health checks.
- Add unit, controller, and application-context tests that run without a real model or API key, plus operator-facing run and test documentation.
- Explicitly exclude Database Agent, datasource access, SQL, tool calling, RAG, MCP, chat memory, streaming, persistence, authorization, and audit storage.

## Capabilities

### New Capabilities

- `ai-chat`: Defines the provider-neutral chat service contract and the synchronous REST chat behavior, including validation and stable success/error responses.
- `model-provider-integration`: Defines configurable Spring AI model access through `ChatClient`/`ChatModel`, secret handling, provider neutrality, and test isolation from real model services.
- `service-operations`: Defines health reporting, safe failure handling, and non-sensitive request/model-call observability for the AI server.

### Modified Capabilities

None.

## Impact

- Creates the complete Maven project structure and initial source/test layout from an otherwise empty repository.
- Adds Spring Boot 4.x, Spring AI 2.x, Bean Validation, Actuator, and their managed test dependencies.
- Introduces the public HTTP endpoint `POST /api/v1/ai/chat` and the internal `AiChatService` API.
- Establishes `com.zorth.aiplatform` as the root Java package and a dependency direction in which `ai-server`, and eventually `ai-agent` and `ai-datasource`, depend on `ai-core`.
- Requires runtime model credentials and endpoint/model settings to be supplied only through external configuration; no credential is committed to the repository.
- Does not modify an existing runtime or API because none exists yet.
