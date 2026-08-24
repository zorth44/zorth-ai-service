## Context

The repository contains a detailed foundation brief but no Java project or production code. This change must establish the smallest useful AI platform baseline before any Database Agent or Text-to-SQL work begins. The platform must expose a stable internal API that is independent of a concrete model provider while using Spring AI as its implementation framework.

The implementation spans build structure, a core library, a Spring Boot server, external model configuration, HTTP behavior, error handling, observability, and isolated tests. The initial runtime target is one OpenAI-compatible provider, but provider-specific SDK types must not leak into controllers or platform contracts.

## Goals / Non-Goals

**Goals:**

- Create a reproducible Java 17+ Maven multi-module build using compatible stable Spring Boot 4.0.x and Spring AI 2.0.x releases.
- Establish `com.zorth.aiplatform` as the package root and enforce one-way module dependencies.
- Provide a minimal synchronous, provider-neutral `AiChatService` and REST endpoint backed by Spring AI `ChatClient`.
- Keep credentials and provider settings outside source control.
- Provide predictable validation and sanitized error behavior.
- Make health and model-call behavior observable without logging prompts, credentials, or full sensitive configuration.
- Ensure the normal automated test suite requires neither network access nor a real provider credential.

**Non-Goals:**

- Database Agent, datasource connectivity, database metadata, SQL generation/execution/guarding, or semantic metadata.
- Tool calling, RAG, vector stores, MCP, memory, conversation persistence, streaming/SSE, dynamic multi-model routing, authorization, or persistent audit storage.
- A generic response framework, a provider plugin system, or speculative abstractions without a current use.
- Active model probes from the health endpoint.

## Decisions

### 1. Use a four-module Maven reactor with a thin executable server

The root project will be a Maven aggregator with `pom` packaging and four child modules:

```text
ai-platform (root)
├── ai-core
├── ai-agent        (empty placeholder)
├── ai-datasource   (empty placeholder)
└── ai-server       (Spring Boot executable)
```

`ai-core` owns the chat contract, request/response records, base AI exception, and the `SpringAiChatService` adapter. It may depend on the provider-neutral Spring AI API that owns `ChatClient`, but it must not depend on a concrete model starter. `ai-server` depends on `ai-core` and owns the OpenAI-compatible starter, web adapter, configuration, exception handler, Actuator, and application bootstrap. `ai-agent` and `ai-datasource` contain only module build descriptors in this change and may depend on `ai-core` only when future code needs it.

This keeps the provider dependency at the application edge while avoiding a fifth adapter module for a single small implementation. A separate `ai-model-adapter` module was considered, but rejected as premature for the MVP.

### 2. Keep the platform API provider-neutral and minimal

`AiChatService` exposes `ChatResponse chat(ChatRequest request)`. The request contains only `message`; the response contains only `content`. Provider names, Spring AI response objects, model options, conversation identifiers, agent identifiers, tools, and memory do not appear in this API.

The same small records may be used at the HTTP boundary for this first endpoint. Separate transport and domain DTOs were considered, but would add mapping with no behavioral distinction in the MVP. They can be split later if the HTTP contract diverges.

### 3. Register framework adapters explicitly in the server

`SpringAiChatService` will be a plain class rather than relying on component scanning across module/package boundaries. An `AiConfiguration` class in `ai-server` will receive the Spring AI auto-configured `ChatClient.Builder`, create a singleton `ChatClient`, and register `SpringAiChatService` as the `AiChatService` bean.

The application remains under `com.zorth.aiplatform.server`; explicit bean registration makes ownership visible and avoids broad `scanBasePackages` configuration. Constructing `OpenAiChatModel` manually was rejected because it would duplicate starter behavior and spread provider configuration into application code.

### 4. Use the official starter and externalized OpenAI-compatible configuration

The root build will import Spring AI dependency management compatible with the chosen Spring Boot version. `ai-server` will use the official OpenAI model starter. Runtime configuration will bind the API key, base URL, model, and temperature through Spring configuration with environment-variable placeholders such as `AI_API_KEY`, `AI_BASE_URL`, and `AI_MODEL`. Temperature defaults to `0.2`; credentials and the model name have no secret or environment-specific hard-coded value.

The exact property keys and compatible patch versions will be verified against the pinned Spring AI 2.0.x release during implementation. Business code will only see `ChatClient` or `AiChatService`, so switching to another Spring AI `ChatModel` implementation must not affect the controller or core service contract.

### 5. Make the chat operation synchronous and deliberately small

`SpringAiChatService` will call `chatClient.prompt().user(message).call().content()` and return that content. The REST adapter exposes `POST /api/v1/ai/chat` with JSON input and output. No streaming, advisors, memory, tools, system-prompt management, retry framework, or fallback routing is introduced.

The request message must be non-null, non-blank, and at most 10,000 characters. Bean Validation is enforced before the service is called.

### 6. Use a minimal explicit error contract

Successful responses remain unwrapped as `{ "content": "..." }`. Errors use a separate minimal shape `{ "code": "...", "message": "..." }`; a generic `ApiResponse<T>` framework is not introduced.

The global exception handler maps validation failures to HTTP 400 with `INVALID_REQUEST`, `AiException` (including translated provider/model failures) to HTTP 500 with `AI_SERVICE_ERROR`, and unhandled failures to HTTP 500 with `INTERNAL_ERROR`. Client responses never include SDK exception details or stack traces; full diagnostic exceptions remain server-side logs.

### 7. Separate liveness from paid provider availability

Spring Boot Actuator exposes `/actuator/health` for application health. No custom health contributor calls the chat model. Provider connectivity is demonstrated only by an explicitly initiated chat request or documented manual integration test.

Model-call logging records start, outcome, elapsed duration, and message length. It does not record prompt content, response content, API keys, authorization headers, or full configuration values.

### 8. Test each boundary without external services

- A core unit test uses a mocked `ChatModel` behind a real or test-built `ChatClient` (or mocks the minimal fluent interaction if required by the pinned API) and verifies content mapping and failure translation.
- A controller slice test mocks `AiChatService` and verifies HTTP 200, JSON shape, and validation rejection without service invocation.
- An application-context test supplies test-only dummy/model bean configuration sufficient for auto-configuration and never performs a model call. No real `AI_API_KEY` is read or required.
- A real provider call is documented as an opt-in manual integration procedure and is not part of `mvn test`.

### 9. Replace the requirements-oriented root README with operator documentation

Once the requirements are captured in OpenSpec, the root README will describe the project, current capability, explicitly unimplemented roadmap, prerequisites, environment variables, startup, curl usage, health check, and `mvn clean test`. It will use placeholders only and will not contain credentials.

## Risks / Trade-offs

- [Spring Boot 4.0.x and Spring AI 2.0.x compatibility may vary by patch release] → Pin a known-compatible stable pair through Maven dependency management and validate the full reactor and context test before accepting the implementation.
- [OpenAI-compatible providers may differ in optional API behavior] → Keep the first flow to basic synchronous text chat and externalize base URL/model settings; document the provider used for manual verification.
- [Placing `SpringAiChatService` in `ai-core` means the module is not completely framework-free] → Depend only on provider-neutral Spring AI client APIs there and keep the concrete starter/configuration in `ai-server`; split an adapter module only when a second implementation creates real pressure.
- [A 10,000-character limit does not guarantee the request fits every model context window] → Treat it as an HTTP abuse/sanity bound; model-specific token policies remain future work.
- [HTTP 500 does not distinguish transient upstream failures from internal failures for automated clients] → Preserve the stable `AI_SERVICE_ERROR` code now; introduce richer gateway/retry semantics only with concrete operational requirements.
- [Dummy context-test configuration can drift from production auto-configuration] → Keep one application-context test close to production wiring and add an opt-in documented real-provider smoke test.

## Migration Plan

There is no existing application or persisted state to migrate. Implement the Maven reactor first, then core API/adapter, server configuration and HTTP layer, operational behavior, tests, and documentation. Validate with `mvn clean test`; manually start the server with externally supplied provider settings and exercise health and chat endpoints. Rollback consists of reverting this initial project scaffold because the change introduces no database or external state migration.

## Open Questions

None blocking. The concrete OpenAI-compatible provider used for manual verification can be selected through environment variables without changing the design.
