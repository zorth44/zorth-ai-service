## Context

The first provider-backed evaluation used DeepSeek `deepseek-v4-flash` against three representative Mapper files. `BloodRuleGroupItemMapper.xml` produced a valid artifact in about 95 seconds. `TaskMapper.xml` produced a valid artifact in about 359 seconds after Spring AI schema correction. `AppealRecordMapper.xml` returned a typed value in about 147 seconds but local validation rejected it because `possibleMeaning` had no matching `BusinessMeaning`. The default Spring AI OpenAI chat timeout of 60 seconds failed before any of these responses could complete; a 300-second timeout was required.

Review of the two published artifacts found that all 18 expected top-level statements and all 30 `TaskMapper` dynamic conditions were present, and fixed predicates were generally accurate. It also found 237 empty strings standing in for optional aliases/meanings, reversed dynamic-filter field semantics, a fabricated `derived_union` physical table for a UNION subquery aliased `bn`, and one high-confidence `BusinessMeaning` for nearly every ordinary CRUD statement. These are systematic contract issues rather than reasons to weaken statement/provenance validation.

Spring AI 2.0.0's local `JsonSchemaGenerator` supports `org.jspecify.annotations.Nullable` when determining required properties and `@JsonPropertyDescription` for schema descriptions. The current records use neither, so every unannotated `String` is required and undescribed. The existing provider test is environment-gated but intentionally throws `AssertionError`, so it is a safety sentinel rather than an executable evaluation path.

## Goals / Non-Goals

**Goals:**

- Make the Java model, generated JSON Schema, prompt, local validator, and published JSON agree on which scalar values are optional and how absence is encoded.
- Remove semantic ambiguity from dynamic filters and relation references through explicit schema descriptions and a typed relation kind.
- Establish one canonical location for inferred business meaning and reduce unsupported or trivial inference.
- Make long-running model calls configurable without creating a second provider or `ChatClient`.
- Replace the provider-test sentinel with a real, explicit, safe generator run and use the same three Mappers as a focused regression sample.

**Non-Goals:**

- Weakening statement coverage, provenance, evidence, confidence-range, JSON round-trip, or batch-isolation checks.
- Adding a SQL parser, database metadata, Java analysis, semantic merging, RAG, or Agent integration.
- Adding a semantic-specific provider, model, API key, or second `ChatClient`.
- Running the complete 20-file repository directory as part of this correction.
- Solving all possible derived-query lineage; phase one only distinguishes physical, derived, CTE, and unknown relation references.

## Decisions

### 1. Advance the artifact contract to schema version 1.1

`MapperSemantic.SCHEMA_VERSION` becomes `1.1`. Version 1.1 adds `TableKind` to `TableRef`, removes `FilterSemantic.possibleMeaning`, and changes optional scalar schema behavior. Existing version 1.0 JSON is not silently read as 1.1 because absent relation kinds and the removed inference field would otherwise hide incompatible meaning. Operators regenerate old artifacts with `overwrite=true`.

Keeping version `1.0` was rejected because these are observable JSON contract changes. Building a version migration layer was rejected because no runtime consumer or persistent store currently depends on the PoC artifacts.

### 2. Annotate optional components with `@Nullable` and describe every model-visible component

Record components that can legitimately be unknown receive `org.jspecify.annotations.Nullable`. This includes table/column aliases, unresolved physical table ownership, optional filter table/column/operator/value, and relationship-side physical table names when the side is a derived relation. Required identifiers, collection fields, statement ids, operations, evidence, and confidence remain required.

Every model-visible record component receives `@JsonPropertyDescription` with a concise semantic definition, expected form, null rule, and an example where ambiguity was observed. Tests inspect the exact schema produced by the same Spring AI `BeanOutputConverter`/schema generator path used by `entity(...)`, not a handwritten approximation. Optional fields must be absent from the schema's `required` array; required fields remain present.

Published Java values use `null` for absent optional data. Empty strings are invalid for optional or required semantic scalars and are rejected locally. Using empty strings as a normalization fallback was rejected because it erases the distinction between absent and known-empty data.

### 3. Add `TableKind` and represent derived relations without invented table names

`TableRef` becomes `TableRef(table, alias, kind)`, and `TableKind` contains `PHYSICAL`, `DERIVED`, `CTE`, and `UNKNOWN`.

- `PHYSICAL`: `table` is the visible database table name; `alias` is optional.
- `DERIVED`: `table` is null and `alias` is the required visible alias of a subquery/UNION relation.
- `CTE`: `table` is the visible CTE name and `alias` is optional.
- `UNKNOWN`: at least one of `table` or `alias` retains a visible source token, without a fabricated descriptive name.

For a join from `(...) bn JOIN tasks t ON t.id = bn.task_id`, the table list contains `(null, "bn", DERIVED)` and `("tasks", "t", PHYSICAL)`. Relationship physical-table fields are null on the derived side while the original expression and evidence retain `bn.task_id`. Strings such as `derived_union` are forbidden unless they literally occur as a relation identifier in the XML.

Treating every FROM item as a physical table was rejected because it creates factual hallucinations. Adding a full relation-lineage tree was rejected as larger than this stabilization change.

### 4. Make dynamic-filter fields mechanically unambiguous

The generated schema and prompt define:

- `parameter`: the MyBatis parameter name or collection name, such as `startTime` or `bddfTaskNumbers`.
- `expression`: only the conditional SQL fragment, such as `o.created_at >= #{startTime}`.
- `condition`: only the MyBatis/OGNL guard from the dynamic tag, such as `startTime != null`.

Neither `expression` nor `condition` may contain a complete `<if>`, `<when>`, or `<foreach>` element. XML excerpts remain in `SemanticEvidence` with `DYNAMIC_XML`. A focused validator rejects values that look like XML in these two fields and rejects a condition that is merely the SQL fragment. Descriptions and few-shot examples are preferred over post-processing field swaps because post-processing could corrupt a legitimately complex expression.

### 5. Remove `FilterSemantic.possibleMeaning` and keep inference only in `BusinessMeaning`

`FilterSemantic` becomes factual only: expression, optional table/column/operator/value, and confidence. All business interpretation is represented once through `BusinessMeaning(name, description, derivedFrom, confidence)` plus `INFERENCE` evidence.

The prompt forbids a `BusinessMeaning` that merely paraphrases the statement id, operation, or generic CRUD purpose. A statement may have an empty business-meaning list. Inference based only on a statement id and predicate must remain below `0.9`; confidence `0.9` or greater requires explicit strong code evidence such as a comment or named fragment that states the business rule. Undocumented literal meanings remain forbidden.

Retaining both inference locations was rejected because the model must keep two independent strings synchronized and one real response already failed that contract. Automatically generating one from the other was rejected because it would turn application normalization into business inference.

### 6. Use the standard shared OpenAI timeout with an explicit environment binding

`application.yml` exposes Spring AI's existing `spring.ai.openai.chat.timeout` through `AI_CHAT_TIMEOUT`, with a documented default of 300 seconds for this application. The timeout remains part of the one shared OpenAI client and therefore also bounds chat and agent model calls. Semantic configuration does not introduce another base URL, model, API key, provider, HTTP client, or `ChatClient`.

The application documents that schema correction may perform additional model calls and that a 300-second per-call timeout is not a batch timeout. Provider retry and schema-correction counts remain framework-managed. A semantic-only timeout was rejected because the HTTP timeout is configured on the shared provider client, not on `ChatClient` request options in the pinned Spring AI API.

### 7. Replace the sentinel with an `ai-server` provider integration test

The intentionally failing test in `ai-semantic` is removed. A real integration test lives in `ai-server`, where the provider starter, shared `ChatClient`, configuration, and complete generator wiring exist. It is tagged `llm-integration`, excluded by default, and gated by `AI_API_KEY`, `SEMANTIC_MAPPER_SOURCE`, and `SEMANTIC_MAPPER_OUTPUT`. Optional normal provider variables and `AI_CHAT_TIMEOUT` use the same application configuration as production.

A Maven `llm-integration` profile selects the tagged test explicitly. The test invokes `MapperSemanticGenerator` through real Spring wiring, requires at least one candidate, checks report invariants, requires at least one published artifact, and verifies each published file deserializes as schema 1.1. It prints only safe counts and relative paths, never prompts, XML, model bodies, or credentials. It does not contain an unconditional failure.

Keeping the integration test in `ai-semantic` was rejected because that would add provider dependencies and duplicate server composition. A shell-only procedure was rejected because it would not provide a repeatable assertion path.

### 8. Re-run a focused three-file regression before broader evaluation

The post-change real-model evaluation uses the already approved sample:

- `AppealRecordMapper.xml`: multiple joins, local include, fixed and dynamic predicates.
- `BloodRuleGroupItemMapper.xml`: mixed CRUD and foreach.
- `TaskMapper.xml`: large dynamic SQL, joins, UNION-derived relation, and schema correction pressure.

Acceptance records per-file duration, schema correction occurrence, generation report, statement coverage, optional null/empty-string counts, dynamic expression/condition correctness, relation kinds, physical-table hallucinations, fixed predicates, and business meanings. The correction is considered stable enough for a later larger sample when all three publish, no optional field uses an empty string, dynamic fields have the specified meanings, `bn` is `DERIVED` without `derived_union`, and generic CRUD statements do not receive unsupported high-confidence business meanings.

## Risks / Trade-offs

- [Schema 1.1 breaks old artifact readers] → No consumer exists yet; bump the version explicitly and require regeneration rather than silent coercion.
- [`@Nullable` makes fields optional rather than forcing explicit JSON null] → Validate the deserialized Java value and publisher output; absent model properties become null and serialize consistently under the application `ObjectMapper`.
- [Descriptions increase prompt/schema tokens] → Keep descriptions concise and focused; the quality gain is expected to outweigh the small token increase.
- [A 300-second shared timeout lengthens failure detection for chat and agent calls] → Document the global scope and keep `AI_CHAT_TIMEOUT` externally adjustable per environment.
- [Schema validation can still make multiple costly correction calls] → Preserve framework correction for correctness, expose durations in evaluation, and use only three files for this regression.
- [Business-meaning triviality is partly semantic and cannot be perfectly validated in Java] → Enforce deterministic rules locally where possible, strengthen prompt/examples, and inspect the focused real-model outputs.
- [Derived relation lineage remains shallow] → Preserve alias, kind, expression, and evidence now; defer a relation tree until project-level semantic modeling.

## Migration Plan

Implement model/enums/annotations first, update prompt and validator, then timeout/configuration, integration wiring, tests, and documentation. Regenerate fixtures and expected JSON as schema 1.1. Run all default Maven tests without provider credentials, then execute the opt-in three-file real-model profile with an approved source and output directory.

Existing schema 1.0 generated files are not migrated in place. Operators use `overwrite=true` to replace them after the change. Rollback restores schema 1.0 code and prompt; schema 1.1 files remain external artifacts and must not be consumed by the rolled-back version.

## Open Questions

None blocking. If the focused run still needs frequent schema correction, provider-native structured output or a smaller extraction schema can be evaluated in a later change rather than expanding this stabilization scope.
