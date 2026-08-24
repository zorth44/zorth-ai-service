## 1. Spring AI and Module Foundation

- [x] 1.1 Inspect the pinned Spring AI 2.0.0 artifacts/documentation available to the build and record the exact `@Tool`, parameter metadata, `ToolContext`, `ChatClient.tools(...)`, per-request tool-context, and native multi-step tool-calling/advisor APIs to use without a custom loop.
- [x] 1.2 Update `ai-agent/pom.xml` with only the provider-neutral Spring AI, validation, logging, and test dependencies required for agent contracts and tools, and add the `ai-agent` dependency to `ai-server` without moving the concrete model starter out of the server edge.
- [x] 1.3 Create the minimal `com.zorth.aiplatform.agent` package structure for service contracts/runtime, tools, models, execution support, and exceptions; confirm no database, SQL, RAG, MCP, workflow, planning, or dynamic registry types are introduced.

## 2. Agent Contract and Execution Infrastructure

- [x] 2.1 Implement validated `AgentRequest(message)`, `AgentResponse(content)`, and provider-neutral `AiAgentService.execute(...)`, keeping Spring AI/provider types and server-controlled identifiers out of the public contract.
- [x] 2.2 Add a shared tool-context key for `requestId`, `ToolExecutionException`, and a small execution helper that reads correlation from `ToolContext`, measures duration, logs tool name/start/success/failure, preserves server diagnostics, and never generically logs complete arguments or results.
- [x] 2.3 Add the named `agent-system-prompt` classpath resource with concise necessary-tool, unnecessary-tool, no-invented-result, and continue-from-results guidance and no fixed tool sequence.

## 3. Foundation Tool Implementations

- [x] 3.1 Implement `DateTools.getCurrentDate` with an injected `Clock`, an explicit selection description, access to request correlation through `ToolContext`, and structured `CurrentDateResult(date, dayOfWeek)` output.
- [x] 3.2 Implement `DateTools.calculateDaysBetween` with explicit start/end date metadata, the documented signed-day convention, structured output, correlated execution logging, and common invalid-input failure handling.
- [x] 3.3 Implement `CalculatorTools.calculate` with `CalculationRequest`, `CalculationResult`, and `Operation`; support add/subtract/multiply/divide using `MathContext.DECIMAL128` and route null input, unsupported operation, and division by zero through `ToolExecutionException`.
- [x] 3.4 Implement `SystemTools.getSystemInfo` with an explicit service-information selection description, correlated execution logging, and structured application-name/environment/version output supplied through constructor configuration rather than hard-coded runtime values.
- [x] 3.5 Inspect generated Spring AI tool definitions/schemas to verify every tool uses `@Tool`, descriptions state what and when, required model arguments are clearly typed, structured outputs serialize, and `requestId` or other server-controlled fields are absent from model-visible parameters.

## 4. Agent Runtime and Centralized Wiring

- [x] 4.1 Implement `SpringAiAgentService` to generate a request ID, log correlated request duration/outcome without content, attach the system prompt, user message, fixed Spring-managed tool instances, and request ID `ToolContext`, then map final `ChatClient` content to `AgentResponse`.
- [x] 4.2 Configure the pinned Spring AI native tool-calling lifecycle/advisor required for automatic result submission and successive model turns, and verify the implementation contains no application-authored `while` loop, recursive dispatcher, forced tool choice, or hard-coded tool sequence.
- [x] 4.3 Add strongly typed server configuration for application name, environment, and version, with externalized values and suitable non-secret defaults/validation, plus centralized Spring beans for `Clock`, tool execution support, all three tool groups, and `AiAgentService`.
- [x] 4.4 Extend the application-context test to verify the fixed tool set and agent service wire successfully while the existing `AiChatService`, `/api/v1/ai/chat`, and non-model-calling health behavior remain intact.

## 5. Agent HTTP and Safe Failure Behavior

- [x] 5.1 Add `AiAgentController` with validated `POST /api/v1/ai/agent`, direct `AgentResponse` JSON output, and `AiAgentService` as its only agent/model-facing dependency.
- [x] 5.2 Extend global exception handling as needed so agent/model/tool failures use a stable client-safe error response while server logs retain diagnostics and responses expose no stack trace, tool input, provider detail, or internal configuration.
- [x] 5.3 Verify a failed tool request remains request-scoped and does not prevent a later unrelated agent or chat request from being processed.

## 6. Automated Verification

- [x] 6.1 Add `DateTools` unit tests with a fixed clock and cases for current date plus positive, zero, and negative date differences, including structured fields and invalid-input behavior.
- [x] 6.2 Add `CalculatorTools` unit tests for add, subtract, multiply, finite/non-terminating divide precision, division by zero, null/unsupported input, and common exception translation.
- [x] 6.3 Add `SystemTools` unit tests proving application name, environment, and version are mapped from supplied Spring configuration rather than hard-coded values.
- [x] 6.4 Add execution-support tests proving a server request ID is available through `ToolContext`, success/failure are recorded with tool name and duration, and full arguments/results are not emitted by the common logging mechanism.
- [x] 6.5 Add `SpringAiAgentService` tests with a mock, fake, or scripted Spring AI boundary to verify final-content mapping, safe failure translation, tool registration, request-ID context construction, and the no-tool path without provider credentials or network calls.
- [x] 6.6 Add a deterministic scripted multi-step tool-calling test when supported by the pinned Spring AI test surface, proving current-date result submission can lead to date-difference execution; if that surface cannot model tool-call responses, document the limitation in the test and cover the lifecycle through wiring plus the manual real-model guide.
- [x] 6.7 Add controller tests for HTTP 200/unwrapped content and null, blank, and oversized HTTP 400 `INVALID_REQUEST` cases with no service call on validation failure, plus sanitized agent/tool error responses.

## 7. Documentation and Acceptance

- [x] 7.1 Add a README Tool Calling section covering architecture, model-controlled arguments versus server-controlled `ToolContext`, required system-info configuration, startup, and a curl example for `/api/v1/ai/agent` while retaining the plain-chat instructions.
- [x] 7.2 Document opt-in real-model checks for current date, `12345 × 6789`, configured system information, days until 2027-01-01, Redis without tools, and calculator failure; state that execution logs are the source of truth and equivalent reasonable multi-step paths are acceptable.
- [x] 7.3 Run `mvn clean test` without real credentials, resolve all reactor/test failures, and confirm the default lifecycle makes no model network call.
- [x] 7.4 Review the implementation against all change specs and the Phase 2 definition of done, confirming observable single/multi/no-tool support, structured results, safe failures, unchanged chat behavior, and absence of custom agent loops, database/SQL, RAG, MCP, multi-agent, workflow, planning, and persistence features.
