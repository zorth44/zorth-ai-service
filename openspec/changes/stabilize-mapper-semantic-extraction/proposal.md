## Why

A real DeepSeek evaluation of three representative Mapper files proved the extraction pipeline works, but only two artifacts were publishable and the successful outputs exposed schema ambiguity: nullable values became empty strings, dynamic-filter fields were reversed, a derived relation became a fabricated physical table, business inferences were over-produced, and the default 60-second model timeout was too short. These contract and operability issues should be corrected before evaluating or processing a broader repository sample.

## What Changes

- **BREAKING**: Advance the Mapper semantic artifact from schema version `1.0` to `1.1`, add a typed relation kind for physical tables, derived tables, and CTEs, and remove the duplicate `FilterSemantic.possibleMeaning` field so business interpretation has one canonical representation.
- Mark genuinely optional record components as nullable in the Spring AI-generated JSON Schema and reject empty strings used as stand-ins for absent values.
- Add explicit model-visible descriptions to every semantic record component, including unambiguous definitions and examples for `DynamicFilterSemantic.expression` and `condition`.
- Represent derived relations without inventing physical table names and preserve their visible aliases/evidence.
- Tighten prompt and validation policy so `BusinessMeaning` is emitted only for non-trivial supported inference, not as a high-confidence paraphrase of every CRUD statement.
- Expose the existing Spring AI OpenAI chat request timeout through documented external configuration, retain one shared provider/`ChatClient`, and document the global effect of that timeout.
- Replace the intentionally failing provider-integration placeholder with an explicitly opted-in, environment-gated real generator test that writes artifacts and reports real success/failure without running in the default Maven lifecycle.
- Re-run the same three representative Mappers after implementation and compare publishability, schema-correction attempts, null handling, dynamic-filter semantics, derived relations, and inference quality.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `mapper-semantic-artifacts`: Revise the artifact contract to schema version `1.1`, define optional values correctly, add relation kinds, remove duplicate filter inference, add schema descriptions, and tighten business-meaning rules.
- `mapper-semantic-generation`: Clarify generated-schema, dynamic-filter, prompt, derived-relation, and local-validation behavior so model output matches the artifact contract without semantic field reversal or fabricated table names.
- `mapper-semantic-operations`: Add externally configurable model timeout behavior and replace the real-model test placeholder with a safe runnable integration entry and repeatable three-file regression procedure.

## Impact

- Changes Java records/enums and generated `*.semantic.json`; existing schema `1.0` artifacts must be regenerated and are not accepted as `1.1` outputs.
- Updates `MapperSemantic` model annotations, prompt content, validator rules, schema inspection tests, fixtures, and documentation in `ai-semantic`.
- Updates Spring/OpenAI timeout configuration and provider-backed integration test wiring at the `ai-server` edge without adding another provider, API key, model configuration, or `ChatClient`.
- Preserves the existing HTTP endpoint, batch report, scanner, output mapping, chat/agent APIs, database tools, and default no-network Maven test behavior.
- Uses the already approved three-file evaluation set (`AppealRecordMapper.xml`, `BloodRuleGroupItemMapper.xml`, and `TaskMapper.xml`) as post-change evidence; no repository-wide run is required by this correction.
