## ADDED Requirements

### Requirement: Provider-neutral agent service contract
The system SHALL expose an internal `AiAgentService` contract under the `com.zorth.aiplatform` namespace that accepts an `AgentRequest` containing only a user message and returns an `AgentResponse` containing only final generated content. The contract SHALL NOT expose provider-specific model types, Spring AI response types, tool callbacks, request identifiers, or tool context values.

#### Scenario: Agent execution returns final content
- **WHEN** a valid agent request completes after zero or more model-selected tool calls
- **THEN** `AiAgentService` returns an `AgentResponse` whose content equals the model's final answer

#### Scenario: Server context stays out of the public contract
- **WHEN** a caller creates an `AgentRequest`
- **THEN** the caller supplies only the user message and does not supply a request ID or other server-controlled tool context

### Requirement: Agent HTTP endpoint
The system SHALL expose `POST /api/v1/ai/agent`, consume `application/json`, return an unwrapped `{ "content": "..." }` JSON response on success, and delegate model interaction exclusively to `AiAgentService`. The existing `POST /api/v1/ai/chat` behavior SHALL remain available and unchanged.

#### Scenario: Valid agent request succeeds
- **WHEN** a client posts `{ "message": "今天是几号？" }` and the agent service returns final content
- **THEN** the endpoint responds with HTTP 200 and the content at the top level of the JSON body

#### Scenario: Existing chat remains separate
- **WHEN** the tool-calling endpoint is added
- **THEN** clients can still call `/api/v1/ai/chat` with its existing contract and no automatic access to agent tools

#### Scenario: Controller does not execute tools
- **WHEN** the agent controller processes a valid request
- **THEN** it invokes `AiAgentService` and does not construct, select, or directly execute a tool

### Requirement: Agent input validation
The system MUST reject an agent message that is null, blank, or longer than 10,000 characters before invoking `AiAgentService`.

#### Scenario: Blank agent message is rejected
- **WHEN** a client posts an agent request whose message contains no non-whitespace characters
- **THEN** the endpoint responds with HTTP 400 and error code `INVALID_REQUEST`, and does not invoke `AiAgentService`

#### Scenario: Oversized agent message is rejected
- **WHEN** a client posts an agent message longer than 10,000 characters
- **THEN** the endpoint responds with HTTP 400 and error code `INVALID_REQUEST`, and does not invoke `AiAgentService`

### Requirement: Framework-managed tool-calling lifecycle
The system SHALL use Spring AI's native `ChatClient` tool-calling lifecycle to let the LLM select registered tools, generate model-controlled arguments, receive application-executed tool results, continue model reasoning, and produce a final answer. Application code MUST NOT implement its own loop, recursion, or fixed workflow for dispatching successive tool calls.

#### Scenario: Single tool call completes
- **WHEN** the model selects one registered tool for a request and that tool returns a result
- **THEN** Spring AI returns the result to the model and the agent service returns the model's subsequent final answer

#### Scenario: Multiple tool calls are supported
- **WHEN** the model needs the current date before calculating the distance to a target date
- **THEN** the runtime can execute `getCurrentDate`, return its result to the model, execute `calculateDaysBetween` from the next model response, and return the eventual final answer without an application-authored loop

### Requirement: Tools are optional
The agent SHALL allow the model to answer directly when no registered tool is necessary and SHALL NOT force every request to contain a tool call.

#### Scenario: General knowledge request uses no tool
- **WHEN** a user asks for an explanation of Redis and the model determines no tool is necessary
- **THEN** the agent returns the model's answer without executing a registered tool

### Requirement: Agent system guidance
The agent SHALL provide a concise system prompt instructing the model to use tools when needed for current information or deterministic operations, avoid unnecessary tool calls, never invent tool results, and continue from actual results. The prompt MUST NOT require a hard-coded sequence of named tools.

#### Scenario: Prompt permits autonomous selection
- **WHEN** the agent sends a request to the model
- **THEN** the model receives tool-use policy guidance but remains responsible for deciding whether and which registered tool to call

