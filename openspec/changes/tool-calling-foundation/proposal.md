## Why

The existing platform can only send a user message to an LLM and return generated text, so it remains a chatbot with no way to invoke deterministic application capabilities. The next platform layer must establish Spring AI tool calling now so later database-agent, Text-to-SQL, RAG, and MCP work can build on a correct, observable reason–act–observe foundation instead of inventing its own agent loop.

## What Changes

- Activate the `ai-agent` module and introduce a provider-neutral `AiAgentService` request/response contract backed by Spring AI `ChatClient` tool calling.
- Expose validated `POST /api/v1/ai/agent` while preserving the existing `/api/v1/ai/chat` endpoint and keeping controllers free of tool-execution logic.
- Register Spring-managed date, calculator, and system-information tools with explicit descriptions, typed model-controlled parameters, and structured results.
- Support single-tool, multi-step, and no-tool model paths through Spring AI's native tool-calling lifecycle; do not implement a custom agent loop or fixed workflow.
- Pass a server-generated request identifier to tools through `ToolContext`, keeping server-controlled context out of the LLM-visible tool schema.
- Add consistent tool failure translation and execution logs containing request ID, tool name, duration, and outcome without establishing unsafe argument logging.
- Add isolated unit and controller tests plus an opt-in manual real-model verification guide covering single-tool, calculator, structured-result, multi-tool, no-tool, and tool-error scenarios.
- Explicitly exclude database access, SQL, datasource and semantic metadata, RAG, vector stores, MCP, multi-agent behavior, planning frameworks, workflow engines, and conversation persistence.

## Capabilities

### New Capabilities

- `agent-execution`: Defines the agent service and REST endpoint, Spring AI-managed tool-calling lifecycle, system guidance, and support for tool, multi-step, and no-tool paths.
- `foundation-tools`: Defines the date, date-difference, calculator, and system-information tool contracts, selection descriptions, typed parameters, structured results, and centralized registration.
- `tool-execution-operations`: Defines server-controlled `ToolContext`, request correlation, safe execution observability, exception handling, and test/manual-verification behavior.

### Modified Capabilities

None. The existing synchronous `ai-chat`, model-provider integration, and service-operations requirements remain valid and `/api/v1/ai/chat` remains unchanged.

## Impact

- Adds production code and tests to the previously placeholder `ai-agent` module and makes `ai-server` depend on and configure that module.
- Adds the public `POST /api/v1/ai/agent` API and internal `AiAgentService`, `AgentRequest`, and `AgentResponse` contracts.
- Uses Spring AI 2.0 tool annotations, tool context, and `ChatClient` tool-calling facilities already aligned with the project's managed Spring AI dependency.
- Adds configuration-backed application/environment/version metadata and operator documentation for observing tool execution.
- Introduces no persistent state, database schema, external service, or breaking change to the existing chat API.
