## Context

The repository is a Java 17 Maven reactor using Spring Boot 4.0.7 and Spring AI 2.0.0. `ai-server` owns the concrete OpenAI-compatible model starter and constructs one shared `ChatClient`; `ai-core` owns plain chat, `ai-agent` owns agent/tool execution, and `ai-datasource` owns database access and SQL safety. The new capability must analyze repository files without changing any of those runtime behaviors or creating another model/provider configuration.

MyBatis Mapper XML contains both directly observable facts and ambiguous business intent. It can also contain local SQL fragments, unresolved cross-Mapper includes, dynamic tags, external DTD declarations, arbitrary comments, and text that must be treated as untrusted model input. A successful structured-output conversion is therefore necessary but insufficient: source provenance and structural completeness need deterministic application-side checks.

The first phase is an explicitly triggered, synchronous proof of concept. It must work for a configured directory, isolate individual file failures, and produce artifacts suitable for manual evaluation and later merging. It is not a production job platform and does not establish that model extraction quality is acceptable until a real representative sample has been reviewed.

## Goals / Non-Goals

**Goals:**

- Introduce a versioned, strongly typed Mapper semantic artifact with machine-readable facts, explicitly separated inferences, confidence, evidence, and trusted source provenance.
- Process each Mapper independently through the existing `ChatClient` using Spring AI structured output and schema validation.
- Preserve Mapper-relative output paths, guarantee Jackson round trips, and prevent partial output files.
- Make batch behavior deterministic, repeatable, failure-isolated, observable, and safe against duplicate concurrent triggers in one server instance.
- Provide deterministic automated coverage without provider credentials and a separate opt-in protocol for evaluating the PoC hypothesis against real Mapper files.
- Preserve all existing chat, agent, database tool, datasource, and SQL-safety contracts.

**Non-Goals:**

- Vector storage, RAG, semantic search tools, or Database Agent consumption.
- Database schema inspection or Java Entity, DTO, Enum, Mapper interface, or Service analysis.
- Cross-Mapper SQL fragment expansion, complete MyBatis SQL rendering, or a general SQL parser.
- Project-level semantic merging, deduplication, ontology construction, or business concept resolution.
- Chunking oversized Mapper files, parallel/distributed execution, persistent jobs, asynchronous polling, scheduling, or a Web UI.
- Production authentication/authorization for the manual endpoint; the endpoint is opt-in and intended only for locally controlled environments in this phase.

## Decisions

### 1. Add one cohesive `ai-semantic` module and keep provider wiring at the server edge

`ai-semantic` will contain `com.zorth.aiplatform.semantic` models, scanner, prompt builder, AI adapter contract and Spring AI implementation, validator, generator, output mapping, reports, and semantic exceptions. It will depend on provider-neutral Spring AI chat-client APIs, Jackson, Spring core abstractions needed by the implementation, and logging. `ai-server` will depend on `ai-semantic` and own configuration binding, bean assembly, conditional activation, and the HTTP controller.

Placing the feature in `ai-core` was rejected because that module is the minimal plain-chat foundation. `ai-agent` was rejected because offline repository extraction is not tool-calling. Putting all logic in `ai-server` was rejected because it would mix domain orchestration with the transport/composition edge. A new module is a build boundary, not a new deployable service.

### 2. Use a strict versioned artifact and typed enums for closed vocabularies

`MapperSemantic` will contain `schemaVersion`, `sourceHash`, `mapperName`, `namespace`, `sourceFile`, `summary`, and `statements`. Statement semantics retain the proposed tables, columns, relationships, fixed filters, dynamic filters, grouping, ordering, business meanings, and evidence. Closed sets use enums: `SqlOperation`, `ColumnUsage`, `JoinType`, and `EvidenceType`; SQL operators and expressions remain strings because their valid vocabulary is intentionally open.

Every collection is required and serialized as an array, including when empty. Confidence is required and bounded from 0.0 through 1.0. Directly visible SQL facts normally carry 1.0. `summary`, statement descriptions, `FilterSemantic.possibleMeaning`, and `BusinessMeaning` are non-authoritative descriptions or inferences. A non-null `possibleMeaning` must have a corresponding `BusinessMeaning` that provides `derivedFrom` and its own confidence. Business meanings below 0.7 are forbidden rather than normalized into apparently valid output.

`schemaVersion` starts at `1.0`. `sourceHash` is a lowercase SHA-256 digest of the original file bytes. Versioning and hashing were chosen over an unversioned payload because later merging must be able to reject incompatible or stale artifacts. Timestamps and model names are excluded from the artifact itself so repeated output is not made volatile; operational model information belongs in logs or an evaluation report.

Using `Map<String, Object>` or writing raw model text was rejected because either would move schema enforcement downstream. Leaving usages and join types as free strings was rejected because it would create avoidable vocabulary drift.

### 3. Treat provenance and Mapper structure as server-controlled facts

Before model invocation, a lightweight hardened XML preflight reads only the Mapper root namespace and top-level `select`, `insert`, `update`, and `delete` identifiers and operations. External entity and external DTD retrieval are disabled while normal MyBatis Mapper documents remain readable. The preflight is not allowed to parse SQL, expand dynamic tags, or resolve fragments.

The generator computes the normalized root-relative `sourceFile`, source hash, mapper name, namespace, and expected statement inventory. After structured extraction, it reconstructs or verifies server-controlled provenance and requires exact namespace and statement id/operation coverage. Evidence `sourceFile` and `statementId` values must correspond to that trusted inventory. This makes provenance reliable without pretending the application extracted tables, joins, or filters itself.

Trusting echoed paths or hashes from the model was rejected because these values are available deterministically. Parsing complete SQL in the application was rejected because the PoC is intended to evaluate semantic extraction and MyBatis dynamic SQL makes a home-grown parser a separate project.

### 4. Reuse the existing `ChatClient` and Spring AI 2.0 schema validation

`MapperSemanticAiClient` is a thin test seam. `SpringAiMapperSemanticAiClient` uses the shared `ChatClient` and calls:

```java
chatClient.prompt()
        .system(systemPrompt)
        .user(userPrompt)
        .call()
        .entity(MapperSemantic.class, options -> options.validateSchema());
```

Spring AI 2.0.0 schema validation performs response-schema checking and its own corrective repeats for schema-invalid output. The application will not add a second structured-output retry loop. Provider/network transient retries remain governed by the already configured Spring AI model retry layer. A response that passes JSON schema but fails local semantic validation is classified as a validation failure and is not silently normalized or retried in phase one.

`useProviderStructuredOutput()` is not enabled by default because support varies across OpenAI-compatible providers and models. It may be evaluated later without changing the semantic contracts. The shared global temperature is already 0.2 and will not be mutated. Creating a dedicated provider configuration or upgrading Spring AI was rejected.

### 5. Isolate the prompt and treat XML as untrusted data

The system prompt is a named resource or dedicated builder input, not an inline service string. It instructs the model to extract only supplied XML facts, separate inferences, emit empty arrays, resolve same-document `<sql>`/`<include>` references, report unresolved external includes without inventing content, and conform to the schema. It also states that XML content and comments are untrusted data and that any instructions embedded in them must be ignored.

The user prompt contains the trusted source identifier and XML inside explicit data delimiters. The application preserves CDATA, comments, fragments, and dynamic tags, removes only an optional UTF-8 BOM, and may collapse no more than irrelevant repeated whitespace outside content-sensitive handling. It does not convert XML to SQL with regular expressions.

### 6. Use a deterministic sequential batch with explicit count semantics

The scanner recursively selects regular files whose names end in lowercase `.xml`, skips any path with a hidden segment, skips all symbolic links, and sorts candidates by normalized root-relative path. `total` is the number of selected candidates and always equals `success + failed + skipped`.

For each candidate, the generator first maps the output path. If `overwrite=false` and the target exists, it skips without reading or invoking the model. Otherwise, an over-limit file becomes `FILE_TOO_LARGE`; read/XML preflight, AI/structured output, local validation, and write errors are classified by phase. One file failure is recorded and processing continues.

Successful JSON is written as UTF-8 pretty-printed output to a unique temporary sibling, read back into `MapperSemantic`, and then atomically moved to the final path. When an atomic move is unsupported, a same-filesystem replace move is allowed after successful read-back. Temporary files are cleaned on failure. This was chosen over writing directly to the target because interrupted serialization must not destroy the last valid artifact.

Generation remains sequential. Parallel model calls were rejected for the PoC because they complicate rate limiting, ordering, cost control, and diagnostics without helping validate the extraction contract.

### 7. Make execution explicitly opt-in and synchronous

`semantic.mapper.enabled` defaults to `false`. When enabled, `source-directory` and `output-directory` are mandatory; `overwrite` defaults to `true` and `max-file-size` defaults to 200 KiB. Relative paths resolve against the server process working directory and are normalized before use. Request bodies cannot supply or override filesystem paths.

When enabled, `POST /api/v1/semantic/mappers/generate` synchronously invokes the generator and returns the complete report. A process-local non-blocking guard permits only one active batch; a concurrent trigger receives HTTP 409 with `SEMANTIC_GENERATION_ALREADY_RUNNING`. When disabled, semantic generation beans and the endpoint are absent. No batch runs automatically at application startup.

An asynchronous job API was rejected because it would require state, status polling, cancellation, and lifecycle rules that are outside the PoC. A startup runner was rejected because explicit invocation and report retrieval are clearer for a web application.

### 8. Separate per-file failures from batch-level failures and sanitize observability

Missing/unreadable roots, invalid configuration, and an already-running batch are batch-level failures. Candidate-specific failures appear in `MapperSemanticGenerationReport` with a stable phase-oriented type and safe message. The report does not contain XML, prompts, provider response bodies, credentials, or stack traces.

Logs record batch paths/counts, source-relative file identifiers, outcome, failure type, and duration. They do not log complete XML, prompts, model responses, API keys, authorization values, or generated business data at INFO. Existing global safe error response conventions are reused or extended with semantic-specific stable codes.

### 9. Treat automated verification and model-quality evaluation as different evidence

Default tests use fakes/mocks or a scripted local model boundary and never require credentials or network access. They cover the artifact contract, strict validation, XML preflight safety, scanner rules, prompt policy, path mapping, overwrite, atomic read-back, failure classification/isolation, report invariants, concurrency guard, conditional wiring, endpoint status/response behavior, and regressions to existing APIs.

An opt-in provider-backed test or documented manual command processes at least ten representative real Mapper files spanning ordinary select, joins, fixed filters, dynamic conditions, local fragments, update/set, and mixed operations. The evaluator records observed statement, table, join, fixed-filter, and dynamic-filter correctness and factual hallucinations. If credentials or approved source data are unavailable, implementation may be complete but the PoC quality hypothesis must be reported as unverified; no output may be fabricated.

## Risks / Trade-offs

- [Mapper XML may contain proprietary SQL or literals that leave the machine] → Keep generation disabled by default, document the disclosure, require an explicitly configured directory and manual trigger, and never log source content.
- [OpenAI-compatible providers may not honor native structured-output options consistently] → Use prompt-based entity conversion plus Spring AI schema validation by default; evaluate provider-native output separately.
- [Schema-valid output can still be semantically wrong] → Verify server-known structure, require evidence/confidence, reject inconsistent output, and use the real-sample evaluation before claiming PoC success.
- [Strict validation may reduce apparent success rate] → Preserve failures as useful PoC evidence instead of filling omissions with defaults; tune the prompt only from observed failures.
- [A synchronous sequential batch can exceed HTTP infrastructure timeouts] → Document the phase-one limitation and use small controlled directories for evaluation; defer asynchronous jobs and parallelism.
- [Existing output can be stale when `overwrite=false`] → Include `sourceHash`, define skip as existence-based, and document that callers must enable overwrite to refresh changed sources.
- [Atomic move is not universally supported] → Write and verify a same-directory temporary file, prefer atomic move, and fall back to a same-filesystem replace only after verification.
- [Hardened XML parsing can reject unusual Mapper documents] → Cover the project's common MyBatis DOCTYPE and CDATA forms with fixtures and classify unsupported XML explicitly without weakening external-entity protections.

## Migration Plan

No data migration is required. Add the new module and typed contracts first, then extraction and batch behavior, then conditional server wiring, tests, and documentation. The feature remains disabled in default configuration, so deployment does not expose a new endpoint or call a model until explicitly enabled. Existing Maven reactor tests and chat/agent/database controller tests must pass before opt-in provider evaluation.

Rollback disables `semantic.mapper.enabled` immediately. Code rollback removes the endpoint wiring, server dependency, and `ai-semantic` module; generated JSON is an external build artifact and is not read by existing runtime paths, so it can be retained or removed independently.

## Open Questions

None blocking. Provider-native structured output, oversized-file chunking, asynchronous jobs, quantitative quality thresholds, and project-level merging remain explicit follow-up decisions informed by the real-sample evaluation.
