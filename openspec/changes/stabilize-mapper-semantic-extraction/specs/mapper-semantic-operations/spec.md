## ADDED Requirements

### Requirement: Shared model request timeout is externally configurable
The application SHALL bind `AI_CHAT_TIMEOUT` to the standard `spring.ai.openai.chat.timeout` duration used by the shared OpenAI-compatible provider client and SHALL default it to 300 seconds. This setting SHALL apply to semantic extraction, chat, and agent model calls because they share one provider client. Semantic generation MUST NOT create a second provider, HTTP client, API key, model setting, or `ChatClient` to obtain a separate timeout.

#### Scenario: Long semantic response can complete
- **WHEN** `AI_CHAT_TIMEOUT=300s` and a single valid semantic model response takes longer than 60 seconds but less than 300 seconds
- **THEN** the provider client continues waiting and the response can proceed to schema and local validation

#### Scenario: Environment can shorten timeout
- **WHEN** an environment sets `AI_CHAT_TIMEOUT=90s`
- **THEN** the shared provider client uses a 90-second request timeout without source changes

#### Scenario: Timeout does not create duplicate AI configuration
- **WHEN** the timeout is configured for semantic evaluation
- **THEN** the existing shared provider client and `ChatClient` remain the only model configuration used by semantic, chat, and agent services

## MODIFIED Requirements

### Requirement: Default verification never calls a paid model
The default Maven test lifecycle SHALL verify semantic artifacts, generated schema descriptions/nullability, scanning, XML preflight, prompting, structured adapter behavior at a fake or scripted boundary, validation, output, reports, concurrency, timeout binding, configuration, endpoint behavior, and existing API regressions without network access or provider credentials. Provider-backed tests MUST be tagged `llm-integration`, gated by required environment variables, excluded from the default lifecycle, and contain no unconditional failure placeholder.

#### Scenario: Maven tests run without API key
- **WHEN** the complete default Maven reactor tests run with no model credentials
- **THEN** semantic tests execute deterministically and no external model request is attempted

#### Scenario: Provider test is not selected by default
- **WHEN** the runnable provider-backed semantic integration test exists
- **THEN** the ordinary test lifecycle excludes it unless the operator explicitly activates the integration profile and supplies configuration

#### Scenario: Provider test is a real generator path
- **WHEN** the provider-backed test source is inspected
- **THEN** it invokes real server-composed semantic generation under environment gates and does not deliberately throw an assertion directing the operator elsewhere

### Requirement: Real-model PoC evaluation is explicit and honest
The application SHALL provide an opt-in Maven integration profile that runs the real configured generator against an approved source directory, writes to an approved output directory, requires at least one candidate and one successfully published schema 1.1 artifact, and verifies report invariants and typed JSON read-back. Stabilization acceptance SHALL use three representative Mapper files covering multiple joins/local include, mixed CRUD/foreach, and large dynamic SQL/UNION-derived relations. The evaluation SHALL record statement coverage, table/relation correctness, fixed filters, dynamic expression/condition correctness, optional null versus empty-string use, business-inference quality, schema correction, duration, and factual hallucinations. If credentials or approved data are unavailable, completion reporting MUST state that model quality remains unverified and MUST NOT fabricate results.

#### Scenario: Environment-gated integration run succeeds
- **WHEN** an operator explicitly activates the `llm-integration` profile with `AI_API_KEY`, `SEMANTIC_MAPPER_SOURCE`, `SEMANTIC_MAPPER_OUTPUT`, and valid provider configuration
- **THEN** the test runs the real generator, publishes schema 1.1 artifacts, checks report invariants and read-back, and reports only safe counts and relative paths

#### Scenario: Three-file stabilization sample is assessed
- **WHEN** `AppealRecordMapper.xml`, `BloodRuleGroupItemMapper.xml`, and `TaskMapper.xml` are supplied as the approved source sample
- **THEN** the evaluation records whether all three publish and whether nullability, dynamic fields, derived relation kind, and business meanings meet the stabilized contract

#### Scenario: Missing opt-in configuration skips provider use
- **WHEN** the integration profile is not active or required environment variables are unavailable
- **THEN** no provider-backed semantic model request runs and no success result is fabricated

### Requirement: Operational documentation discloses boundaries
Documentation SHALL describe configuration, `AI_CHAT_TIMEOUT` and its shared/global scope, startup, manual invocation, opt-in integration-profile invocation, output mapping, report fields, overwrite and schema-1.0 regeneration behavior, sequential synchronous execution, schema-correction cost, the one-batch guard, default-disabled state, and the fact that selected Mapper content is sent to the configured external model provider. It SHALL list phase-one non-goals, advise against exposing the unauthenticated PoC endpoint publicly, and identify the supported three-file stabilization checklist.

#### Scenario: Operator understands data disclosure
- **WHEN** an operator follows the semantic generation documentation
- **THEN** the operator is informed before invocation that Mapper XML leaves the process for the configured model provider

#### Scenario: Operator understands timeout scope
- **WHEN** an operator raises `AI_CHAT_TIMEOUT` for semantic extraction
- **THEN** documentation states that the same timeout also applies to chat and agent calls using the shared provider client

#### Scenario: Operator can run the real integration entry
- **WHEN** an operator has approved data and credentials
- **THEN** documentation gives a working Maven profile command and required environment variables rather than pointing to an intentionally failing placeholder

#### Scenario: Limitations are not presented as implemented
- **WHEN** documentation lists later semantic-platform work
- **THEN** database enrichment, Java analysis, semantic merging, RAG, agent consumption, chunking, and asynchronous jobs are clearly marked as not implemented
