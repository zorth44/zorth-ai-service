## 1. Module and Artifact Foundation

- [x] 1.1 Add `ai-semantic` to the root Maven reactor, create its `pom.xml` with only provider-neutral Spring AI chat-client, Jackson, Spring utility/configuration, logging, and test dependencies, and add the module dependency to `ai-server` without adding or upgrading a model provider starter.
- [x] 1.2 Create the `com.zorth.aiplatform.semantic` package structure for model, scan/preflight, prompt, AI adapter, validation, generation/output, reporting, and exceptions while keeping web/configuration classes in `ai-server`.
- [x] 1.3 Implement `MapperSemantic`, `MapperStatementSemantic`, `TableRef`, `ColumnRef`, `RelationshipSemantic`, `FilterSemantic`, `DynamicFilterSemantic`, `BusinessMeaning`, and `SemanticEvidence` with the required version/provenance fields and non-null collection contract.
- [x] 1.4 Implement `SqlOperation`, `ColumnUsage`, `JoinType`, and `EvidenceType` closed vocabularies with explicit `UNKNOWN` handling where specified.
- [x] 1.5 Add artifact unit tests for record construction, enum JSON values, empty-array serialization, confidence boundaries, business-meaning threshold behavior, forbidden arbitrary-map/provider fields, and Jackson serialize-deserialize equality.
- [x] 1.6 Implement and test lowercase SHA-256 hashing of original source bytes plus normalized forward-slash root-relative source paths, including rejection of paths outside the configured root.

## 2. Mapper Discovery and Safe XML Preflight

- [x] 2.1 Add UTF-8 test Mapper resources for ordinary SELECT, LEFT JOIN, dynamic `if/where/choose/when`, local `sql/include`, dynamic UPDATE `set`, mixed select/insert/update/delete, unresolved include, typical MyBatis DOCTYPE/CDATA, malformed XML, hidden paths, and oversized input.
- [x] 2.2 Implement deterministic recursive `MapperFileScanner` behavior for lowercase `.xml` regular files, normalized-path sorting, hidden-path exclusion, and complete symbolic-link exclusion.
- [x] 2.3 Add scanner tests for nested order, lowercase suffix filtering, hidden files/directories, file and directory symlinks, empty roots, and no traversal outside the configured root.
- [x] 2.4 Implement hardened Mapper XML preflight that accepts common MyBatis documents, prevents external entity and external DTD retrieval, and extracts only namespace plus top-level select/insert/update/delete ids and operations.
- [x] 2.5 Add preflight tests for valid namespace/inventory, mixed operations, duplicate or blank statement ids, non-Mapper XML, malformed XML, MyBatis external DTD declarations without I/O, CDATA, and malicious external-entity input.
- [x] 2.6 Implement strict UTF-8 reading with optional leading BOM removal for prompt content while retaining original bytes for hashing, and add invalid-UTF-8 and BOM tests.

## 3. Prompt and Spring AI Structured Extraction

- [x] 3.1 Add the semantic extraction system prompt as a named resource or isolated prompt component with fact/inference, confidence, evidence, empty-array, dynamic-tag, local-include, unresolved-include, null-not-guessing, schema, and embedded-instruction rules.
- [x] 3.2 Implement `MapperSemanticPromptBuilder` with explicit untrusted-data delimiters and trusted source/provenance context while preserving XML comments, CDATA, fragments, includes, and dynamic tags.
- [x] 3.3 Add prompt tests proving the policy text is stable, one Mapper is included per request, source values are substituted safely, dynamic/local fragment content is preserved, and malicious XML comments cannot alter the system prompt.
- [x] 3.4 Introduce the thin `MapperSemanticAiClient` seam and implement `SpringAiMapperSemanticAiClient` with the shared `ChatClient`, direct `MapperSemantic` entity conversion, and `validateSchema()` enabled.
- [x] 3.5 Verify in code and tests that semantic extraction does not call `.content()` for publication, enable provider-native structured output by default, mutate global temperature, configure a second provider/API key, or implement an application structured-output retry loop.
- [x] 3.6 Add AI-adapter tests at a mocked or scripted Spring AI boundary for typed success, null response, provider failure, exhausted schema-validation failure, and safe exception translation without credentials or network calls.

## 4. Provenance, Semantic Validation, and Single-File Extraction

- [x] 4.1 Implement `MapperSemanticValidator` for required scalars and collections, enum presence, confidence range, business-meaning threshold/correspondence, mandatory evidence, and evidence consistency.
- [x] 4.2 Add validator comparison against trusted preflight data for exact namespace, mapper name, statement id/operation coverage, duplicate/hallucinated statements, and statement evidence ownership.
- [x] 4.3 Implement trusted provenance enforcement so schema version, source hash, top-level source path, and evidence source paths in the publishable value come only from application-computed inputs rather than model claims.
- [x] 4.4 Add validator/provenance tests for blank values, null lists, missing/duplicate/extra statements, wrong operations, invalid confidence, weak or unmatched inference, foreign evidence ids, and model-echoed path/hash mismatches.
- [x] 4.5 Implement `MapperSemanticExtractor` to read and hash one file, run XML preflight, build the prompt, call the AI adapter once, enforce provenance, validate semantics, and return the typed result.
- [x] 4.6 Add extractor tests for valid results plus `READ_ERROR`, `XML_VALIDATION_ERROR`, `AI_CALL_ERROR`, `STRUCTURED_OUTPUT_ERROR`, and `VALIDATION_ERROR` mapping, proving invalid input never reaches later stages.

## 5. Output Publication and Batch Generation

- [x] 5.1 Implement and test collision-safe output mapping that preserves all relative directories, replaces only the final `.xml` suffix with `.semantic.json`, creates no path outside the normalized output root, and distinguishes same-named Mappers in different modules.
- [x] 5.2 Implement the JSON publisher using the injected application-compatible `ObjectMapper`, UTF-8 pretty printing, a unique same-directory temporary file, typed read-back/equality verification, preferred atomic replacement, safe same-filesystem fallback, and temporary cleanup.
- [x] 5.3 Add publisher tests for first write, successful overwrite, serialization/read-back/move failures, cleanup, and preservation of a previous valid target on every failed replacement path.
- [x] 5.4 Implement `MapperSemanticFailureType`, safe failure details, and `MapperSemanticGenerationReport` with enforced `total = success + failed + skipped` and `failures.size() = failed` invariants.
- [x] 5.5 Implement sequential `MapperSemanticGenerator` orchestration with stable scan order, overwrite-before-read behavior, byte-size enforcement, per-file extraction/publication, failure classification, and continuation after every candidate-specific failure.
- [x] 5.6 Add generator tests for empty input, all-success, mixed success/failure/skip, oversized files without AI calls, overwrite false without reads or AI calls, failed overwrite preserving output, sorted invocation order, and sanitized reports.
- [x] 5.7 Review the generator for absence of parallel model calls, cross-Mapper expansion, file chunking, SQL regex parsing, database/Java enrichment, semantic merging, persistence, RAG, and agent tool registration.

## 6. Conditional Server Wiring and Manual Operation

- [x] 6.1 Add strongly typed `semantic.mapper` server configuration with `enabled=false`, required paths only when enabled, `overwrite=true`, `max-file-size=200KiB`, path normalization, and positive-limit validation.
- [x] 6.2 Extend centralized server configuration to conditionally assemble the semantic scanner, preflight, prompt builder, shared-`ChatClient` AI adapter, validator, publisher, generator, and existing `ObjectMapper` without affecting current beans when disabled.
- [x] 6.3 Implement a process-local non-blocking single-batch guard with guaranteed release after success or failure and a stable already-running exception.
- [x] 6.4 Add `POST /api/v1/semantic/mappers/generate` only when enabled, with no request-body paths/content, `MapperSemanticGenerator` as its sole generation dependency, synchronous unwrapped report output, and HTTP 409 `SEMANTIC_GENERATION_ALREADY_RUNNING` handling.
- [x] 6.5 Extend safe server exception handling for configuration/runtime semantic batch failures while keeping candidate-specific failures in HTTP 200 reports and excluding absolute sensitive details, XML, prompts, provider bodies, credentials, and stack traces.
- [x] 6.6 Add configuration/context/controller tests for default-disabled startup, enabled defaults and validation, conditional endpoint presence, successful report shape, unexpected body/path override rejection, invalid-root safe failure, concurrent 409, and guard release after exception.
- [x] 6.7 Add logging tests or focused review proving batch/file paths, counts, failure types, outcomes, and durations are observable while complete XML, prompts, responses, generated JSON, headers, and credentials are absent from INFO logs.

## 7. Offline Verification and Real-Model Evaluation

- [x] 7.1 Ensure all default semantic tests use fakes, mocks, or scripted local boundaries and add a build check proving no provider credential or network request is required by the ordinary Maven test lifecycle.
- [x] 7.2 Add an explicitly opt-in provider-backed semantic integration test or runnable verification entry that is skipped unless the operator supplies valid model configuration and approved Mapper input.
- [x] 7.3 Document the at-least-ten-Mapper evaluation protocol and review checklist for statement coverage, table correctness, JOIN correctness, fixed-filter correctness, dynamic-filter usefulness, and factual hallucinations across all required fixture categories.
- [x] 7.4 When approved credentials and source data are available, run the real-model evaluation and record observed results; otherwise record that engineering verification passed but the model-quality hypothesis remains unverified without fabricating output.

## 8. Documentation, Regression, and Spec Acceptance

- [x] 8.1 Update README or focused documentation with architecture, all configuration fields/defaults, data-provider disclosure, startup, synchronous curl invocation, report/output examples, overwrite/staleness rules, sequential and single-batch limits, and safe handling guidance for the unauthenticated PoC endpoint.
- [x] 8.2 Document phase-one limitations explicitly: Mapper XML only, local includes only, no DB schema or Java analysis, no oversized-file chunking, no project merger/store/RAG/agent consumption, no async jobs, and no production access control.
- [x] 8.3 Run the complete Maven reactor tests without real provider credentials and fix all semantic and existing chat/agent/datasource/server regressions.
- [x] 8.4 Review the implementation against all three change specs and the original Definition of Done, verifying every normative scenario is implemented or covered and no existing API/provider behavior changed.
