## Why

The Database Agent can inspect live database structure but has no structured access to the SQL intent, relationships, filters, and dynamic behavior already encoded in MyBatis Mapper XML. A bounded Mapper XML-to-semantic JSON proof of concept is needed to determine whether the configured LLM can extract those repository facts reliably enough to support a later project-level semantic model without introducing RAG, persistence, or agent integration yet.

## What Changes

- Add a typed, provider-neutral semantic artifact for one MyBatis Mapper, including statements, tables, columns, relationships, filters, dynamic filters, business inferences, confidence, evidence, schema version, and source provenance.
- Add a batch generator that recursively scans a configured Mapper root, processes each XML file independently through the existing Spring AI `ChatClient`, validates structured output locally, and writes Jackson-round-trippable `*.semantic.json` files while preserving relative paths.
- Add stable overwrite, size-limit, symbolic-link, failure-isolation, atomic-write, and batch-report behavior so repeated runs are observable and safe.
- Add an opt-in, synchronous manual HTTP trigger backed only by configured server-side paths, with one active batch per application instance and safe logs/errors.
- Add deterministic automated tests that never call a paid model by default, plus an opt-in real-model evaluation procedure for at least ten representative Mapper files.
- Add a dedicated `ai-semantic` Maven module and wire it at the `ai-server` edge without changing existing chat, agent, or database tool behavior.
- Keep vector storage, RAG, database schema ingestion, Java source analysis, cross-Mapper include expansion, project-level semantic merging, and Database Agent consumption out of scope.

## Capabilities

### New Capabilities

- `mapper-semantic-artifacts`: Defines the versioned Java/JSON contract, fact-versus-inference rules, confidence and evidence constraints, source provenance, and serialization guarantees for one Mapper.
- `mapper-semantic-generation`: Defines deterministic scanning, per-file structured extraction, validation, output mapping, atomic persistence, retry boundaries, failure isolation, and generation reports.
- `mapper-semantic-operations`: Defines opt-in configuration, the manual trigger, single-batch concurrency behavior, safe observability, and offline versus real-model verification.

### Modified Capabilities

None. Existing chat, agent execution, datasource, SQL safety, and database tool requirements remain unchanged.

## Impact

- Adds the `ai-semantic` Maven module and a dependency on it from `ai-server`; the existing Spring AI version and provider starter remain unchanged.
- Adds `semantic.mapper.*` configuration and `POST /api/v1/semantic/mappers/generate`, both disabled by default unless explicitly configured.
- Reuses the existing shared `ChatClient`, application `ObjectMapper`, logging conventions, and safe server error handling; it does not create another provider configuration or alter global model options.
- Sends configured Mapper XML content to the configured model provider when a generation run is explicitly triggered, and writes generated artifacts under the configured output directory.
- Adds test Mapper fixtures, unit/component tests, documentation, and an opt-in provider-backed evaluation workflow.
