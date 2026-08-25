# Mapper Semantic Generation

This is a phase-one proof of concept: convert one MyBatis Mapper XML file at a time into a typed `MapperSemantic` JSON artifact. It is **not** a semantic platform, RAG store, or Database Agent feature.

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

There is no second model provider, API key, base URL, model name, or temperature setting for this feature. It reuses the shared `ChatClient`.

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

1. Configure model credentials as usual (`AI_API_KEY`, optional `AI_MODEL` / `AI_BASE_URL`).
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

## Limits

- Candidates are processed **sequentially**.
- Only one batch may run per application instance.
- A long directory can exceed HTTP timeouts; use a small controlled tree for this PoC.
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

### Evaluation protocol (opt-in)

Use at least **ten** approved Mapper files covering:

1. Ordinary SELECT
2. LEFT JOIN
3. Dynamic `if` / `where`
4. `choose` / `when` / `otherwise`
5. Local `sql` / `include`
6. Dynamic UPDATE `set`
7. Mixed select/insert/update/delete
8. Unresolved include
9. Typical MyBatis DOCTYPE / CDATA
10. Nested module paths (same filename in different directories)

The repository fixtures under `ai-semantic/src/test/resources/mappers/` can be copied into an approved source directory after review.

Checklist for each file:

| Check | Pass if |
| --- | --- |
| Statement coverage | Every top-level select/insert/update/delete id and operation is present exactly once |
| Tables | Visible tables/aliases are correct; unknown ownership is null, not invented |
| JOIN | Explicit joins have the right tables/columns, `JoinType`, expression, and confidence `1.0` |
| Fixed filters | Literal predicates are preserved; undocumented status codes are not given invented meanings |
| Dynamic filters | `if`/`where`/`choose` conditions are useful and not flattened into fake fixed SQL |
| Hallucinations | No invented tables, statements, includes, or unresolved fragment content |

To run:

1. Export a valid `AI_API_KEY` and point the app at an approved Mapper directory.
2. Enable `semantic.mapper.*` and start the server, **or** run:

```bash
mvn -pl ai-semantic -Dgroups=llm-integration test
```

only after implementing/replacing the skipped integration entry with a real run against approved data.

3. Inspect `*.semantic.json` and fill the checklist. Record observed pass/fail counts. Do not fabricate results.

### Evaluation status

Engineering verification (offline Maven tests) is implemented.

**Real-model quality hypothesis: NOT RUN.** Approved production Mapper data and operator credentials were not used in this change. Do not treat fixture-based unit tests as evidence that the LLM extraction quality is acceptable.

## Safe handling of the unauthenticated endpoint

- Keep `semantic.mapper.enabled=false` in shared environments.
- Enable only on a locally controlled machine.
- Do not put this endpoint behind a public ingress without access control (out of scope here).
- Treat generated JSON as derived source: it may contain business identifiers copied from Mapper XML.
