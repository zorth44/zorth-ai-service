## 1. Upgrade the Semantic Artifact Model

- [x] 1.1 Bump generated mapper semantic artifacts to schema version `1.1` and update version-aware serialization and validation tests.
- [x] 1.2 Add `TableKind` with `PHYSICAL`, `DERIVED`, `CTE`, and `UNKNOWN`, extend `TableRef` with `kind`, and enforce the null/name/alias rules for every kind.
- [x] 1.3 Remove `FilterSemantic.possibleMeaning` from the public artifact model and migrate fixtures, serializers, validators, and consumers to `BusinessMeaning` as the only inference representation.
- [x] 1.4 Mark every optional record component with the supported nullability annotation and add explicit JSON Schema descriptions to record components whose meaning or format is not self-evident.
- [x] 1.5 Add focused schema-generation tests proving optional components are nullable and not required, record component descriptions are emitted, and removed fields no longer appear in the model-visible JSON Schema.

## 2. Align Extraction Semantics and Validation

- [x] 2.1 Update the structured-output prompt and examples so `DynamicFilterSemantic.expression` contains SQL only, `condition` contains the MyBatis OGNL test only, and neither contains the surrounding XML element.
- [x] 2.2 Update table extraction guidance and examples to classify subquery aliases as `DERIVED`, CTE references as `CTE`, and physical tables as `PHYSICAL` without inventing table names.
- [x] 2.3 Tighten the `BusinessMeaning` prompt rules so generic CRUD paraphrases are omitted and confidence `>= 0.9` requires explicit, strong evidence from identifiers, comments, or surrounding code.
- [x] 2.4 Extend local validation to reject empty-string substitutes for nullable values, invalid `TableKind` combinations, XML embedded in dynamic filter fields, and duplicate or legacy inference representations.
- [x] 2.5 Add unit fixtures covering nullable values, derived tables, dynamic filters, absent business meanings, and evidence-backed business meanings, including negative validation cases.

## 3. Make Model Timeout Configurable

- [x] 3.1 Bind the shared Spring AI OpenAI chat timeout to `AI_CHAT_TIMEOUT` with a documented default of `300s`, without introducing a second chat client or mapper-only timeout path.
- [x] 3.2 Add configuration tests for the default timeout and environment override, and verify the setting applies consistently to mapper extraction and other users of the shared chat model.

## 4. Complete the Real-Provider Integration Test

- [x] 4.1 Replace the unconditional placeholder failure with an `ai-server` real-provider test that invokes the production mapper semantic generator and reads back the generated artifact.
- [x] 4.2 Gate the test on explicit enablement plus required provider/source/output environment variables, tag it `llm-integration`, and keep it excluded from normal unit and reactor test runs.
- [x] 4.3 Add a Maven profile or equivalent documented entry point that runs the tagged integration test and reports a clear skipped reason when prerequisites are absent.
- [x] 4.4 Assert the integration result contains validated schema `1.1` artifacts while ensuring credentials and submitted Mapper XML are not exposed in test output or logs.

## 5. Verify Representative Repository Extraction

- [x] 5.1 Update operator and developer documentation for schema `1.1`, `AI_CHAT_TIMEOUT`, the breaking removal of `possibleMeaning`, and the real-provider integration-test command.
- [x] 5.2 Run the default offline reactor tests and confirm they remain deterministic and make no paid-provider calls.
- [x] 5.3 With explicit provider authorization, run the completed integration entry point against `AppealRecordMapper.xml`, `BloodRuleGroupItemMapper.xml`, and `TaskMapper.xml` from the representative repository.
- [x] 5.4 Inspect the provider results and record the acceptance matrix, including the honest failed criteria when `TaskMapper.xml` is rejected before publication or unsupported high-confidence business meanings are found.
