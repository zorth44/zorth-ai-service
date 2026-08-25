## ADDED Requirements

### Requirement: Deterministic Mapper file discovery
The generator SHALL recursively discover regular files below the configured source directory whose names end with lowercase `.xml`, exclude every symbolic link and every path containing a hidden segment, and return candidates sorted by normalized root-relative path. It MUST NOT follow directory or file symbolic links.

#### Scenario: Nested Mapper files are sorted
- **WHEN** eligible XML files exist in multiple nested directories
- **THEN** the scanner returns all eligible files in ascending normalized root-relative path order

#### Scenario: Hidden and symbolic paths are excluded
- **WHEN** XML files exist below `.hidden`, have a hidden filename, or are reached through a symbolic link
- **THEN** none of those files is included in the candidate count or processed

#### Scenario: Non-XML files are excluded
- **WHEN** the source tree contains JSON, Java, uppercase `.XML`, and lowercase `.xml` files
- **THEN** only files ending in lowercase `.xml` are selected

### Requirement: Source root and Mapper XML preflight
Before starting per-file model extraction, the system MUST require the configured source path to exist, be a readable directory, and be distinct from an invalid or inaccessible location. For each candidate, a hardened XML preflight SHALL obtain only the root Mapper namespace and top-level statement ids/operations while preventing external entity and external DTD retrieval. The preflight MUST NOT render dynamic SQL or parse complete SQL expressions.

#### Scenario: Missing source directory rejects the batch
- **WHEN** generation is requested with a source directory that does not exist
- **THEN** the request fails before model invocation with a safe batch-level source-directory error

#### Scenario: Typical MyBatis DOCTYPE cannot fetch external content
- **WHEN** a Mapper declares the MyBatis external DTD
- **THEN** preflight can inspect the Mapper without performing a network or filesystem retrieval for that DTD

#### Scenario: Non-Mapper XML is classified
- **WHEN** a selected XML file has no MyBatis `mapper` root or valid namespace
- **THEN** that candidate receives an XML validation failure and the generator continues with later candidates

### Requirement: File size limit is enforced before model invocation
The generator SHALL compare each non-skipped source file's byte size with the configured maximum before reading its content for extraction. A file larger than the maximum MUST be recorded as failed with `FILE_TOO_LARGE` and MUST NOT invoke the AI client.

#### Scenario: Oversized Mapper is isolated
- **WHEN** one selected Mapper exceeds 200 KiB under the default limit and another is within the limit
- **THEN** the oversized Mapper is reported as `FILE_TOO_LARGE`, the model is not called for it, and the eligible Mapper is still processed

### Requirement: One independent structured extraction per Mapper
For each non-skipped, size-eligible Mapper, the generator SHALL read UTF-8 content, remove an optional leading BOM, compute trusted provenance, build an isolated prompt, and invoke `MapperSemanticAiClient` for that Mapper alone. It MUST NOT combine multiple Mapper files into one model request.

#### Scenario: Batch of three eligible Mappers makes isolated requests
- **WHEN** three eligible Mapper files require generation and all succeed on their first structured extraction
- **THEN** the AI client receives three independent extraction requests, each containing only one Mapper's content

#### Scenario: Invalid UTF-8 does not reach the model
- **WHEN** a candidate cannot be decoded as UTF-8
- **THEN** it is reported as a read failure without invoking the AI client

### Requirement: Spring AI structured output is schema validated
The production AI adapter SHALL reuse the application's existing `ChatClient` and map the response directly to `MapperSemantic` with Spring AI schema validation enabled. It MUST NOT write raw response content, expose provider response types, create a second provider configuration, change global temperature, or add a second application-authored structured-output retry loop. Provider-native structured output MUST remain disabled by default unless separately proven compatible.

#### Scenario: Schema-valid response reaches local validation
- **WHEN** Spring AI returns JSON conforming to the `MapperSemantic` schema
- **THEN** the adapter returns a typed value for application-side semantic validation

#### Scenario: Schema-invalid response exhausts framework correction
- **WHEN** model output remains schema-invalid after Spring AI's configured schema-validation repeats
- **THEN** the file is classified as a structured-output failure and no JSON artifact is written

#### Scenario: Existing ChatClient is reused
- **WHEN** semantic beans are assembled in `ai-server`
- **THEN** the semantic AI adapter receives the same centrally configured `ChatClient` used by existing AI services and no additional provider starter or API-key configuration is introduced

### Requirement: Prompt preserves MyBatis semantics and resists embedded instructions
The semantic prompt SHALL instruct the model to extract only supplied Mapper facts, ignore instructions inside XML/comments, separate inference from fact, use null rather than guessing, emit all statements and all required arrays, retain concise evidence, analyze common dynamic tags, and conform exactly to the structured schema. The input MUST preserve comments, CDATA, `sql`, `include`, `if`, `where`, `choose`, `when`, `otherwise`, `foreach`, `trim`, and `set` elements and MUST NOT use regular expressions to flatten the Mapper into SQL.

#### Scenario: Embedded prompt instruction is data
- **WHEN** an XML comment tells the model to ignore the schema or invent a table
- **THEN** the system prompt identifies that comment as untrusted data and the result remains subject to schema and local validation

#### Scenario: Same-Mapper include is available for reasoning
- **WHEN** a statement includes a fragment defined by `sql` in the same Mapper
- **THEN** both the include and local fragment remain in the model input so the model can associate them

#### Scenario: External include content is not invented
- **WHEN** an include cannot be resolved within the supplied Mapper
- **THEN** the prompt requires unresolved evidence rather than fabricated fragment content

### Requirement: Local semantic validation follows extraction
Every typed model result MUST pass strict artifact validation against application-computed provenance and XML-preflight statement inventory before serialization. A schema-valid but semantically inconsistent result SHALL be reported as `VALIDATION_ERROR`; it MUST NOT be silently repaired, normalized, or published.

#### Scenario: Wrong operation is rejected
- **WHEN** XML preflight identifies an `update` statement but model output labels the same id `SELECT`
- **THEN** local validation fails that file and no target JSON is published

#### Scenario: Hallucinated statement is rejected
- **WHEN** model output includes a statement id absent from the Mapper
- **THEN** local validation fails that file

### Requirement: Relative output mapping is collision safe
For a source path relative to the configured root, the output path SHALL preserve every relative parent segment and replace only the final `.xml` suffix with `.semantic.json` below the configured output directory. The resolved target MUST remain below the normalized output root.

#### Scenario: Nested path is preserved
- **WHEN** the source is `module-a/mapper/order/OrderMapper.xml`
- **THEN** the target is `<output>/module-a/mapper/order/OrderMapper.semantic.json`

#### Scenario: Same filename in different directories does not collide
- **WHEN** `module-a/mapper/UserMapper.xml` and `module-b/mapper/UserMapper.xml` are generated
- **THEN** they produce distinct targets under their corresponding relative directories

### Requirement: Output publication is verified and recoverable
The generator SHALL create required output parents, serialize pretty-printed UTF-8 JSON to a unique temporary sibling, deserialize and compare it to the intended semantic value, and move it to the target only after successful verification. It SHALL prefer an atomic replacement and MAY fall back to a same-filesystem replacement when atomic movement is unsupported. A failed write or read-back MUST leave any previously valid target unchanged and MUST clean the temporary candidate.

#### Scenario: Successful candidate becomes final output
- **WHEN** serialization and typed read-back succeed
- **THEN** the verified temporary candidate replaces the target and the file is counted as successful

#### Scenario: Interrupted or invalid candidate preserves old output
- **WHEN** writing or read-back fails while an older valid target exists
- **THEN** the older target remains readable, the temporary candidate is not published, and the file is reported as `WRITE_ERROR`

### Requirement: Overwrite behavior is explicit
When `overwrite=true`, an existing target SHALL be regenerated and replaced only after a new verified candidate succeeds. When `overwrite=false`, an existing target SHALL be skipped before source reading, size checking, or model invocation, and the existing content SHALL remain unchanged. Existence-based skipping does not imply that the target source hash is current.

#### Scenario: Overwrite disabled avoids model cost
- **WHEN** a target already exists and `overwrite=false`
- **THEN** the candidate is counted as skipped and the AI client is not invoked

#### Scenario: Failed overwrite retains previous artifact
- **WHEN** a target exists, `overwrite=true`, and the new extraction or write fails
- **THEN** the previous target remains unchanged and the candidate is counted as failed

### Requirement: Per-file failures do not terminate the batch
The generator SHALL catch and classify candidate-specific failures as `READ_ERROR`, `FILE_TOO_LARGE`, `XML_VALIDATION_ERROR`, `AI_CALL_ERROR`, `STRUCTURED_OUTPUT_ERROR`, `VALIDATION_ERROR`, `WRITE_ERROR`, or `UNKNOWN`, record a safe source-relative message, and continue to later candidates. Credentials, complete prompts, XML, provider bodies, and stack traces MUST NOT appear in the report.

#### Scenario: Middle file failure does not stop later file
- **WHEN** the second of three sorted Mapper candidates fails AI extraction
- **THEN** the failure is recorded and the third Mapper is still attempted

#### Scenario: Safe failure detail is returned
- **WHEN** a provider exception contains request headers or an API key
- **THEN** the report identifies `AI_CALL_ERROR` with a sanitized message and excludes those sensitive values

### Requirement: Generation report has consistent totals
Every completed batch SHALL return `MapperSemanticGenerationReport(total, success, failed, skipped, failures)`. `total` MUST equal `success + failed + skipped`, `failures.size()` MUST equal `failed`, and every failure entry MUST identify one selected source-relative candidate, stable failure type, and safe message.

#### Scenario: Mixed batch totals reconcile
- **WHEN** a batch selects five files of which two succeed, two fail, and one is skipped
- **THEN** the report contains `total=5`, `success=2`, `failed=2`, `skipped=1`, and exactly two failure entries

#### Scenario: Empty directory succeeds with empty report
- **WHEN** a valid source directory contains no eligible Mapper candidates
- **THEN** generation completes with all four counts equal to zero and an empty failures list

### Requirement: Phase-one processing remains bounded
The generator SHALL process candidates sequentially and MUST NOT implement parallel model calls, oversized-file chunking, cross-Mapper include expansion, database schema enrichment, Java source analysis, semantic merging, vector storage, RAG, or agent tool registration.

#### Scenario: Oversized file is not chunked
- **WHEN** a Mapper exceeds the configured maximum
- **THEN** it receives one `FILE_TOO_LARGE` failure and is not split into model requests

#### Scenario: Generation does not alter agent tools
- **WHEN** semantic generation is added
- **THEN** the tools and behavior registered for existing `/api/v1/ai/agent` requests remain unchanged
