# Mapper Semantic Generation

This is a phase-one proof of concept: convert one MyBatis Mapper XML file at a time into a typed schema `1.1` `MapperSemantic` JSON artifact. It is **not** a semantic platform, RAG store, or Database Agent feature.

**Data disclosure:** when generation runs, the selected Mapper XML is sent to the **already configured external model provider**. Do not enable this against confidential repositories unless that disclosure is acceptable. The PoC HTTP endpoint is unauthenticated and must not be exposed publicly.

## Architecture

```text
Configured source directory
  → MapperFileScanner
  → per file: size check → UTF-8 read/hash → XML preflight
  → MapperSemanticPromptBuilder
  → shared ChatClient structured output (schema validation)
  → provenance rewrite + MapperSemanticValidator
  → atomic *.semantic.json publish
  → MapperSemanticGenerationReport
```

`ai-semantic` owns the domain types and pipeline. `ai-server` owns `semantic.mapper` configuration, bean assembly, and `POST /api/v1/semantic/mappers/generate`. Existing `/api/v1/ai/chat` and `/api/v1/ai/agent` behavior is unchanged. Semantic generation does not register Agent tools.

## Configuration

All fields use the `semantic.mapper` prefix. Generation is **disabled by default** and does not run at startup.

| Property | Default | Notes |
| --- | --- | --- |
| `semantic.mapper.enabled` | `false` | When false, beans and the HTTP endpoint are absent. Source/output paths are not required. |
| `semantic.mapper.source-directory` | none | Required when enabled. Relative paths resolve against the process working directory and are normalized. |
| `semantic.mapper.output-directory` | none | Required when enabled. Same resolution/normalization rules. |
| `semantic.mapper.overwrite` | `true` | When false, an existing target is skipped before reading the source or calling the model. Skip does **not** mean the `sourceHash` is current. |
| `semantic.mapper.max-file-size` | `200KB` (200 KiB) | Compared to source byte size before extraction. Oversized files are `FILE_TOO_LARGE` and are not chunked. |
| `spring.ai.openai.chat.timeout` / `AI_CHAT_TIMEOUT` | `300s` | Per model call, including any framework schema-correction call. This is shared by semantic extraction, chat, and agent calls; it is not a batch timeout. |

There is no second model provider, API key, base URL, model name, temperature, HTTP client, or timeout path for this feature. It reuses the shared `ChatClient`. Lowering or raising `AI_CHAT_TIMEOUT` therefore also changes the timeout for `/api/v1/ai/chat` and `/api/v1/ai/agent` model calls.

Example:

```yaml
semantic:
  mapper:
    enabled: true
    source-directory: /path/to/project/src/main/resources/mapper
    output-directory: ./semantic-output
    overwrite: true
    max-file-size: 200KB
```

## Startup and invocation

1. Configure model credentials as usual (`AI_API_KEY`, optional `AI_MODEL` / `AI_BASE_URL` / `AI_CHAT_TIMEOUT`).
2. Set `semantic.mapper.enabled=true` and the two directory properties.
3. Start the server. No scan or model call happens until the endpoint is invoked.
4. Trigger one synchronous batch:

```bash
curl -X POST http://localhost:8080/api/v1/semantic/mappers/generate
```

The request body cannot supply Mapper XML or filesystem paths. Unexpected JSON is ignored; only server configuration is used.

Success is HTTP 200 with an unwrapped report:

```json
{
  "total": 5,
  "success": 2,
  "failed": 2,
  "skipped": 1,
  "failures": [
    {
      "sourceFile": "order/OrderMapper.xml",
      "type": "VALIDATION_ERROR",
      "message": "Statement coverage does not match the Mapper XML"
    }
  ]
}
```

`total` always equals `success + failed + skipped`. `failures.length` equals `failed`. Candidate-specific failures stay in this HTTP 200 report. Missing or unreadable configured roots return a stable batch error such as `SEMANTIC_SOURCE_DIRECTORY_INVALID` without stack traces or Mapper content.

A second request while a batch is running receives HTTP 409:

```json
{
  "code": "SEMANTIC_GENERATION_ALREADY_RUNNING",
  "message": "Mapper semantic generation is already running"
}
```

The guard is process-local and is released after success or failure.

## Output mapping and overwrite

`module-a/mapper/order/OrderMapper.xml` becomes `<output>/module-a/mapper/order/OrderMapper.semantic.json`. Same filenames in different directories do not collide. JSON is pretty-printed UTF-8, read back as `MapperSemantic`, then moved into place (atomic when possible). A failed write leaves any previous valid target unchanged.

When `overwrite=false`, existing targets are skipped and may be stale relative to the current source hash. Set `overwrite=true` to regenerate.

Schema `1.0` artifacts are incompatible with schema `1.1` and are not migrated or relabeled. Regenerate them from the original Mapper XML with `overwrite=true`. Schema `1.1` adds `TableKind`, makes genuinely optional scalar properties optional in the model-visible schema, and removes `FilterSemantic.possibleMeaning`; all supported inference now appears only in `businessMeanings`.

## Limits

- Candidates are processed **sequentially**.
- Only one batch may run per application instance.
- A long directory can exceed HTTP timeouts; use a small controlled tree for this PoC.
- Structured-output schema correction can make additional paid model calls. `AI_CHAT_TIMEOUT` applies to each call separately and does not bound the complete file or batch duration.
- INFO logs include configured directories, relative source paths, counts, failure types, outcomes, and duration. They do not include complete XML, prompts, model responses, generated JSON, API keys, or provider headers.

## Phase-one limitations (not implemented)

- Mapper XML only. No Java Entity/DTO/Enum/Mapper-interface/Service analysis.
- Local `<sql>` / `<include>` only. Cross-Mapper includes are reported as unresolved, not expanded.
- No database schema inspection or enrichment.
- No oversized-file chunking.
- No project-level semantic merge, store, RAG, vector search, or Database Agent consumption.
- No asynchronous jobs, scheduling, polling, or Web UI.
- No production authentication or authorization on the trigger endpoint.

## Default tests versus real-model evaluation

`mvn test` uses fakes, mocks, and local fixtures. It must not require `AI_API_KEY` or a network model call. Provider-backed tests are tagged `llm-integration` and are excluded from the ordinary Surefire lifecycle.

### Stabilization evaluation protocol (opt-in)

The supported stabilization sample is exactly three reviewed Mapper files:

1. `AppealRecordMapper.xml`: multiple joins, local include, fixed predicates, and dynamic predicates.
2. `BloodRuleGroupItemMapper.xml`: mixed CRUD and `foreach`.
3. `TaskMapper.xml`: large dynamic SQL, joins, and a UNION-derived relation aliased `bn`.

Copy only those approved files into a dedicated source directory. The test recursively scans that directory and sends every candidate Mapper XML to the configured external provider.

Checklist for each file:

| Check | Pass if |
| --- | --- |
| Statement coverage | Every top-level select/insert/update/delete id and operation is present exactly once |
| Optional values | Unknown aliases/owners/operators/values are JSON null, never empty strings |
| Tables | Physical relations are `PHYSICAL`; subquery alias `bn` is `DERIVED` with a null table; `derived_union` is not invented |
| JOIN | Explicit joins have the right tables/columns, `JoinType`, expression, and confidence `1.0` |
| Fixed filters | Literal predicates are preserved; undocumented status codes are not given invented meanings |
| Dynamic filters | `expression` is SQL only, `condition` is OGNL only, and XML remains in evidence |
| Business meanings | Generic CRUD statements have no unsupported meaning; confidence `>= 0.9` has explicit strong code evidence |
| Hallucinations | No invented tables, statements, includes, or unresolved fragment content |

To run:

1. Export a valid `AI_API_KEY` and optional normal provider settings:

```bash
export AI_API_KEY='...'
export AI_BASE_URL='https://api.deepseek.com'
export AI_MODEL='deepseek-v4-flash'
export AI_CHAT_TIMEOUT='300s'
```

2. Point the integration runner at dedicated approved source and output directories, then explicitly activate the profile:

```bash
export SEMANTIC_MAPPER_SOURCE='/path/to/approved-three-mappers'
export SEMANTIC_MAPPER_OUTPUT='/path/to/semantic-evaluation-output'
mvn -pl ai-server -am -Pllm-integration test
```

The tagged test uses the production server composition and real `MapperSemanticGenerator`, requires at least one candidate and one published artifact, checks report invariants, and reads every output back as schema `1.1`. If any required environment variable is absent, JUnit reports the provider test as skipped and no model call occurs. Test output contains safe counts and output-relative artifact paths only; it never intentionally prints credentials, Mapper XML, prompts, or model response bodies.

3. Inspect `*.semantic.json` and fill the checklist. Record observed pass/fail counts. Do not fabricate results.

### Evaluation status

Engineering verification (offline Maven tests) is implemented.

**Schema `1.1` real-model quality: RUN, NOT ACCEPTED (2026-08-25).** The approved three-file profile published two preliminary artifacts and rejected `TaskMapper.xml` because a dynamic field contained enclosing XML. Acceptance review then found unsupported high-confidence meanings in the preliminary `AppealRecordMapper` artifact and tightened the final validator to require literal comment or named `<sql>` evidence. `BloodRuleGroupItemMapper` met the inspected shape checks; `bn=DERIVED` remains unverified because no final `TaskMapper` artifact was published. See the change evaluation record for the full safe-count matrix. Do not expand to the complete Mapper directory yet.

## Safe handling of the unauthenticated endpoint

- Keep `semantic.mapper.enabled=false` in shared environments.
- Enable only on a locally controlled machine.
- Do not put this endpoint behind a public ingress without access control (out of scope here).
- Treat generated JSON as derived source: it may contain business identifiers copied from Mapper XML.
