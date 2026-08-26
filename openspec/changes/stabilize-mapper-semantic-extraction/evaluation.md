# Schema 1.1 Provider Evaluation

Evaluation date: 2026-08-25

The opt-in `ai-server` `llm-integration` profile was run against the approved three-file sample with the configured OpenAI-compatible provider. No credentials, Mapper XML, prompts, or model response bodies were recorded here.

## Runs

The first complete schema `1.1` run finished in 887.7 seconds:

| Mapper | Result | Evidence |
| --- | --- | --- |
| `AppealRecordMapper.xml` | Published, then rejected during acceptance review | 9 statements, 0 empty strings, 96 JSON nulls, 2 dynamic filters, 0 dynamic XML/condition-SQL violations, 2 derived relations, 0 `derived_union` tables, and no legacy `possibleMeaning`. It contained 4 business meanings at confidence `>= 0.9` without literal source comment or named `<sql>` evidence. This exposed an overly permissive strong-evidence validator, which was tightened; the preliminary artifact is not accepted under the final rule. |
| `BloodRuleGroupItemMapper.xml` | Published | 6 statements, 0 empty strings, 32 JSON nulls, no dynamic filters, no business meanings, no fabricated derived table, and no legacy `possibleMeaning`. |
| `TaskMapper.xml` | `VALIDATION_ERROR` | No artifact was published. |

After the strong-evidence validator was tightened, `TaskMapper.xml` was run alone through the final pipeline. It finished in 589.4 seconds and was rejected with `Dynamic filter fields must not contain enclosing XML`. This confirms the final validator prevents the reversed/embedded representation from being published, but it leaves the `bn` derived-relation output unavailable for inspection.

## Acceptance

| Criterion | Result |
| --- | --- |
| Optional values use JSON null, not empty strings | Pass for both published preliminary artifacts (0 empty strings). |
| Dynamic expression and condition are separated | Pass for published artifacts; final `TaskMapper` output was rejected for violating the rule. |
| Alias `bn` is `DERIVED` and `derived_union` is absent | Not verified because final `TaskMapper` output was rejected before publication. |
| Generic or unsupported high-confidence meanings are absent | `BloodRuleGroupItemMapper` passes. `AppealRecordMapper` exposed unsupported high-confidence meanings; the final validator now rejects that shape. |
| All three files publish | Fail (2/3 in the preliminary run; `TaskMapper` failed final validation). |

The correction successfully enforces the stabilized contract and prevents ambiguous artifacts from being published, but the configured model does not yet pass the complete three-file quality gate. Do not expand to the full Mapper directory until prompt/model behavior is improved and all three files publish under the final validator.
