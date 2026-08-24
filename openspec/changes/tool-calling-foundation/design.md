## Context

Phase 1 provides a synchronous `/api/v1/ai/chat` path through `AiChatService`, a Spring-managed `ChatClient`, and an OpenAI-compatible `ChatModel`. The `ai-agent` module is currently an empty Maven placeholder. The platform therefore has no tool definitions, agent-facing service boundary, server-controlled tool context, or way to observe a model-selected application action.

This change activates `ai-agent` without introducing a business-specific agent. It must demonstrate the Spring AI-managed lifecycle `LLM → tool selection → application execution → tool result → LLM`, including more than one tool call when the model decides it is necessary. The implementation must preserve the existing chat behavior, keep provider types at the server edge, avoid real-model calls in the default test suite, and leave database, SQL, RAG, MCP, workflow, planning, and persistence concerns for later changes.

## Goals / Non-Goals

**Goals:**

- Add a small, provider-neutral `AiAgentService` API and validated REST endpoint alongside the existing chat API.
- Let Spring AI own tool-call dispatch, result submission, and continued model interaction, with no application-authored agent loop.
- Provide date, date-difference, calculator, and configuration-backed system-information capabilities with useful schemas and structured results.
- Establish a strict boundary between model-controlled arguments and server-controlled `ToolContext` values.
- Correlate agent and tool execution, record safe timing/outcome logs, and translate failures without leaking internals.
- Verify deterministic tool behavior and HTTP/service boundaries automatically, while documenting opt-in real-model scenarios for selection quality and multi-step behavior.

**Non-Goals:**

- Database connectivity, datasource registries, database metadata, SQL generation or execution, semantic metadata, and SQL safety controls.
- RAG, vector storage, MCP, multi-agent orchestration, workflow engines, planning frameworks, memory, or conversation persistence.
- Dynamic tool registries, tool marketplaces, runtime tool discovery, per-tenant tool policy, or persistent tool audit records.
- Prompt-enforced fixed tool sequences or deterministic claims about the exact choices of every model/provider.

## Decisions

### 1. Activate `ai-agent` as the owner of agent contracts, runtime, tools, and tool models

`ai-agent` will contain `AiAgentService`, `AgentRequest`, `AgentResponse`, `SpringAiAgentService`, the concrete tool classes, their input/result records and enum, `ToolExecutionException`, and a small shared execution-observability helper. It will depend on `ai-core` only for shared platform exception behavior if needed and on provider-neutral Spring AI APIs. `ai-server` will depend on `ai-agent`, expose `AiAgentController`, and explicitly provide runtime/configuration beans.

Keeping the new boundary in `ai-agent` makes that module cohesive and leaves `ai-core` focused on provider-neutral plain chat. Moving all agent interfaces into `ai-core` was considered, but rejected because no non-agent consumer currently needs them. Putting tools in `ai-server` was rejected because it would mix reusable application capabilities with the HTTP/bootstrap layer.

### 2. Use an independent agent service and endpoint rather than changing chat semantics

The new contract is `AgentResponse execute(AgentRequest request)`, where request contains only `message` and response contains only `content`. `POST /api/v1/ai/agent` validates the same null, blank, and 10,000-character bounds used by chat, delegates only to `AiAgentService`, and returns the unwrapped response. `/api/v1/ai/chat` and `AiChatService` remain unchanged.

This makes tool-enabled and plain-chat behavior observable without adding a mode flag whose semantics would leak across clients. Extending `AiChatService` with tool options was considered, but would complicate the minimal Phase 1 contract and make accidental tool access harder to reason about.

### 3. Delegate the complete agent lifecycle to Spring AI

`SpringAiAgentService` will build an agent prompt with the user message, the `agent-system-prompt`, the fixed set of Spring-managed tool objects, and a per-request tool-context map, then execute it through `ChatClient`. The Spring AI tool-calling facility compatible with the pinned 2.0.0 API—its advisor when required by that API, otherwise the built-in `ChatClient` tool execution path—will handle tool call requests, Java invocation, structured-result serialization, result messages, repeated model calls, and final text extraction.

Application code will contain no `while`/recursive tool-call dispatch and no ordered `Tool A → Tool B` prompt rule. Direct `ChatModel` orchestration and a custom registry/loop were rejected because they duplicate framework behavior and obscure the learning objective.

### 4. Keep the system prompt small and policy-oriented

The agent-specific prompt will be stored as a named classpath resource and instruct the model to use tools for current facts or deterministic operations, avoid unnecessary calls, never invent results, and continue from actual tool results. Tool-selection details remain in each tool description. The prompt will not prescribe exact call sequences.

Embedding a rigid multi-step recipe was rejected because it would test a workflow rather than autonomous model selection. Reusing the plain-chat prompt was rejected because tool-use and no-invented-result guidance are agent-specific.

### 5. Register a fixed Spring-managed tool set with explicit schemas

The agent receives exactly `DateTools`, `CalculatorTools`, and `SystemTools` through constructor injection and passes those instances to `ChatClient`. Registration is centralized in configuration; controllers never construct or invoke tools. Each `@Tool` description states both what the method does and when it is appropriate. Model-visible parameters use descriptive names and explicit typed requiredness; server state is never a regular parameter.

- `DateTools.getCurrentDate` returns `CurrentDateResult(LocalDate date, String dayOfWeek)` using an injected `Clock`.
- `DateTools.calculateDaysBetween` accepts known start/end dates and returns a structured signed day difference; positive means the end is after the start, zero means equal, and negative means the end is before the start.
- `CalculatorTools.calculate` accepts `CalculationRequest(BigDecimal left, BigDecimal right, Operation operation)` and returns `CalculationResult(BigDecimal result)`. It supports add, subtract, multiply, and divide. Division uses `MathContext.DECIMAL128` so non-terminating decimal results are defined; zero divisors and invalid/null inputs fail consistently.
- `SystemTools.getSystemInfo` returns `SystemInfo(applicationName, environment, version)` populated from strongly typed Spring configuration supplied by `ai-server`, never hard-coded runtime values.

A global dynamic registry was rejected as unnecessary. Individual `add`, `subtract`, and similar tool methods were rejected because one typed calculator contract better exercises structured parameters without proliferating tools.

### 6. Generate request correlation in the service and pass it only through `ToolContext`

`SpringAiAgentService` generates a request ID for every execution and includes it in the `toolContext` map under one shared constant. Tool methods that need correlation declare Spring AI's `ToolContext` alongside model-controlled input and read the request ID from it. The framework excludes that context object from the model-visible JSON schema.

The request ID will never appear in `AgentRequest`, a tool input record, or an LLM-generated argument. This establishes the pattern later contexts can extend with authenticated `userId`, `tenantId`, `datasourceId`, and `executionId`. Asking the model to echo a correlation value was rejected because it is forgeable and needlessly exposes server state.

### 7. Use a small shared execution helper for consistent logs and failure translation

Concrete tool methods wrap their deterministic operation with an injected helper that records start time, resolves the request ID from `ToolContext`, logs start and completion/failure with `requestId`, `toolName`, duration, and status, and converts expected or unexpected execution failures to `ToolExecutionException`. It does not generically log argument values or results. Agent execution similarly logs request start, completion/failure, and duration without message or response content.

`SpringAiAgentService` catches failures leaving the Spring AI/tool boundary and translates them to the existing safe platform exception path. The HTTP handler returns a stable client-safe error and never exposes tool stack traces, configuration, arguments, or provider details; diagnostic stack traces remain server-side. Swallowing errors into fabricated successful results was rejected because it would let the model treat a failed action as fact. A cross-cutting dynamic proxy/AOP framework was considered, but a small explicit helper is sufficient for three tool classes and keeps behavior visible.

### 8. Separate deterministic automated tests from provider-dependent selection tests

Default tests will cover every arithmetic operation and division by zero; valid current date; positive, zero, and negative date differences; configuration-to-`SystemInfo` mapping; request ID availability through `ToolContext`; safe success/failure logging behavior where practical; service content/failure mapping at a mocked or scripted Spring AI boundary; and controller 200/400 behavior with no real provider call. A deterministic scripted `ChatModel` test will be used if the pinned Spring AI test surface can express sequential tool-call responses without network access; otherwise framework lifecycle verification remains at the service wiring boundary.

README manual verification will exercise a real configured model for single-date, multiplication, system-info structured result, multi-step date calculation, no-tool explanation, and calculator-error scenarios. Logs—not answer text alone—are the evidence that tools executed. Requiring exact call order in default tests was rejected because provider/model selection is probabilistic and would make the build depend on credentials and network access.

## Risks / Trade-offs

- [Spring AI 2.0.0 may expose the tool lifecycle through a different concrete advisor/API name than earlier documentation] → Confirm the pinned dependency's actual API during implementation and use its native `ChatClient` mechanism without changing the no-custom-loop decision.
- [A real model may solve arithmetic itself or choose a different valid multi-step path] → Make tool descriptions precise, keep the prompt neutral, and evaluate actual execution logs and reasonable equivalent paths in the manual guide.
- [Models differ in record/enum schema handling] → Use small records, descriptive parameter annotations, conventional JSON types, and unit tests for all deterministic Java behavior.
- [Tool exceptions may be surfaced differently by providers or the Spring AI callback path] → Translate all failures at the tool and agent boundaries, preserve server diagnostics, and assert only the stable HTTP error contract.
- [A signed date difference may surprise callers expecting an absolute value] → Document the sign convention in the tool description and result model so all three date orderings are deterministic.
- [Configuration-backed system metadata can be absent in a developer environment] → Define explicit non-secret local defaults where appropriate and fail startup for any field that must be supplied; never guess metadata inside the tool.
- [Explicit wrappers add a small amount of repetition to each tool] → Keep one helper and three cohesive tool classes; defer AOP or a global execution framework until the tool count justifies it.

## Migration Plan

No data migration is required. Implement the `ai-agent` dependency and classes first, then server configuration/controller, tests, and README guidance. Run the complete Maven reactor and verify existing `/api/v1/ai/chat` and health tests still pass before manually exercising `/api/v1/ai/agent` with external provider settings. Rollback removes the new endpoint, server dependency/configuration, and `ai-agent` code; no persistent or external state needs restoration.

## Open Questions

None blocking. The implementation must confirm the exact native tool-calling/advisor APIs available in the already pinned Spring AI 2.0.0 dependency before choosing concrete imports.
