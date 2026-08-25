## ADDED Requirements

### Requirement: Semantic generation is disabled by default
The application SHALL expose `semantic.mapper.enabled` with default `false`. When disabled, semantic generator server wiring and its HTTP controller MUST be absent, no source or output directory MUST be required, and no generation or model call may run automatically at startup.

#### Scenario: Default application starts without semantic paths
- **WHEN** the application starts with no `semantic.mapper` configuration
- **THEN** existing services start normally, no semantic endpoint is registered, and no semantic model request occurs

#### Scenario: Disabled configuration never scans
- **WHEN** semantic paths are present but `enabled=false`
- **THEN** application startup and ordinary API requests do not scan those paths or invoke semantic extraction

### Requirement: Enabled configuration is strongly typed and validated
When enabled, configuration SHALL require non-null `source-directory` and `output-directory`, provide `overwrite=true` and `max-file-size=200KiB` defaults, normalize paths, and reject non-positive file-size limits. Relative paths SHALL resolve against the application process working directory. Configuration MUST NOT contain a second model provider, API key, base URL, model name, or global temperature.

#### Scenario: Missing enabled path fails startup safely
- **WHEN** `semantic.mapper.enabled=true` but a required directory property is absent
- **THEN** application configuration fails with a clear property-validation error before serving requests

#### Scenario: Defaults are applied
- **WHEN** semantic generation is enabled with source and output directories but no overwrite or size values
- **THEN** overwrite is true and the maximum source-file size is 200 KiB

### Requirement: Manual generation endpoint uses configured paths only
When enabled, the system SHALL expose synchronous `POST /api/v1/semantic/mappers/generate`, accept no Mapper content or filesystem path from the request body, delegate only to `MapperSemanticGenerator`, and return the unwrapped generation report as JSON. The controller MUST NOT read files, construct prompts, call `ChatClient`, serialize output, or select provider options itself.

#### Scenario: Valid manual generation returns report
- **WHEN** the enabled endpoint is called and the generator completes a mixed batch
- **THEN** HTTP 200 returns top-level `total`, `success`, `failed`, `skipped`, and `failures` fields from the generator report

#### Scenario: Request cannot override directories
- **WHEN** a caller sends a body containing a different source or output path
- **THEN** the endpoint does not use caller-supplied paths and handles the unexpected body according to its no-body HTTP contract

#### Scenario: Disabled endpoint is absent
- **WHEN** `semantic.mapper.enabled=false`
- **THEN** `POST /api/v1/semantic/mappers/generate` is not mapped

### Requirement: Only one batch runs per application instance
The semantic operation layer SHALL use a process-local non-blocking guard so at most one generation batch is active. A concurrent request MUST fail immediately with HTTP 409, stable code `SEMANTIC_GENERATION_ALREADY_RUNNING`, and a safe message; it MUST NOT wait, start another scan, invoke the model, or modify output.

#### Scenario: Concurrent trigger is rejected
- **WHEN** one generation request is still active and a second request arrives
- **THEN** the second receives HTTP 409 with `SEMANTIC_GENERATION_ALREADY_RUNNING` and only the original batch continues

#### Scenario: Guard is released after failure
- **WHEN** a batch ends with a batch-level exception
- **THEN** the guard is released and a later request can start a new batch

### Requirement: Batch-level errors use stable safe responses
Invalid or inaccessible configured roots and other batch-level semantic failures SHALL produce stable semantic error codes and client-safe messages through the server exception path. Responses MUST NOT expose stack traces, absolute secret-bearing configuration, Mapper content, prompts, provider response bodies, or credentials. Candidate-specific failures SHALL remain inside an HTTP 200 generation report rather than converting the whole completed batch to HTTP 500.

#### Scenario: Invalid source root is a safe endpoint error
- **WHEN** the configured source root becomes unavailable before a manual run
- **THEN** the endpoint returns a stable semantic batch error without a stack trace or Mapper content

#### Scenario: Per-file model failure remains report data
- **WHEN** one Mapper has an AI failure but the generator completes the batch
- **THEN** the endpoint returns HTTP 200 with that sanitized failure in the report

### Requirement: Semantic logs are useful and content safe
The application SHALL log batch start and completion with normalized configured directories, candidate counts, outcomes, and duration, and SHALL log each candidate outcome by root-relative source path and failure type. INFO logs MUST NOT contain complete Mapper XML, complete prompts, raw model responses, generated semantic JSON, API keys, authorization values, or provider headers.

#### Scenario: Successful batch is observable
- **WHEN** generation completes
- **THEN** logs show total, success, failed, skipped, and duration and allow successful candidates to be identified by relative path

#### Scenario: Secrets are excluded
- **WHEN** a model failure contains a credential in its exception details
- **THEN** normal semantic logs do not contain that credential or the raw exception message at INFO

### Requirement: Default verification never calls a paid model
The default Maven test lifecycle SHALL verify semantic artifacts, scanning, XML preflight, prompting, structured adapter behavior at a fake or scripted boundary, validation, output, reports, concurrency, configuration, endpoint behavior, and existing API regressions without network access or provider credentials. Provider-backed tests MUST be explicitly tagged or otherwise opt-in and skipped by default.

#### Scenario: Maven tests run without API key
- **WHEN** the complete Maven reactor tests run with no model credentials
- **THEN** semantic tests execute deterministically and no external model request is attempted

#### Scenario: Provider test is not selected by default
- **WHEN** a provider-backed semantic integration test exists
- **THEN** the ordinary test lifecycle excludes it unless the operator explicitly opts in and supplies configuration

### Requirement: Real-model PoC evaluation is explicit and honest
Documentation SHALL provide an opt-in procedure that runs the implemented generator against at least ten approved representative Mapper files and records observed statement coverage, table correctness, join correctness, fixed-filter correctness, dynamic-filter usefulness, and factual hallucinations. The sample SHALL include ordinary select, join, dynamic SQL, local fragment/include, update/set, and mixed-operation cases. If credentials or approved source data are unavailable, completion reporting MUST state that model quality remains unverified and MUST NOT fabricate semantic output or evaluation results.

#### Scenario: Representative evaluation can be performed
- **WHEN** an operator supplies approved Mapper data and valid model configuration
- **THEN** the documented procedure produces semantic JSON and a review checklist covering the required extraction categories for at least ten files

#### Scenario: No credentials means no claimed quality result
- **WHEN** implementation is completed without an available model credential
- **THEN** automated engineering verification may pass but the handoff explicitly marks the real-model PoC evaluation as not run

### Requirement: Operational documentation discloses boundaries
Documentation SHALL describe configuration, startup, manual invocation, output mapping, report fields, overwrite and stale-output behavior, sequential synchronous execution, the one-batch guard, default-disabled state, and the fact that selected Mapper content is sent to the configured external model provider. It SHALL list the phase-one non-goals and advise against exposing the unauthenticated PoC endpoint publicly.

#### Scenario: Operator understands data disclosure
- **WHEN** an operator follows the semantic generation documentation
- **THEN** the operator is informed before invocation that Mapper XML leaves the process for the configured model provider

#### Scenario: Limitations are not presented as implemented
- **WHEN** documentation lists later semantic-platform work
- **THEN** database enrichment, Java analysis, semantic merging, RAG, agent consumption, chunking, and asynchronous jobs are clearly marked as not implemented

### Requirement: Existing runtime contracts remain compatible
Adding semantic generation MUST NOT change the request/response contracts, tool registration, provider configuration, or behavior of `/api/v1/ai/chat`, `/api/v1/ai/agent`, database tools, datasource operations, or health checks when semantic generation is disabled or enabled.

#### Scenario: Existing endpoints remain operational
- **WHEN** the application includes the semantic module
- **THEN** existing chat, agent, and health tests continue to pass with their prior contracts

#### Scenario: Semantic run does not register agent tools
- **WHEN** a semantic batch is triggered
- **THEN** it uses direct structured extraction and does not add a semantic tool to any current agent request
