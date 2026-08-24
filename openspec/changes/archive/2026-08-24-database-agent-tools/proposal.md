## Why

The platform can already run Spring AI tool calling, but database questions still have no safe, model-driven path to inspect schema or run SQL. The current Text-to-SQL idea of stuffing schema into a prompt and hoping the model writes SQL is not a product boundary: it skips discovery, validation, and server-side safety. This change introduces a LangChain `SQLDatabaseToolkit`-style Database Agent on top of the existing agent foundation so the model can list tables, read schema, check SQL, and execute read-only queries through tools.

## What Changes

- Activate the placeholder `ai-datasource` module with a named datasource registry, JDBC access, metadata inspection, SQL validation, and read-only query execution.
- Add Spring AI Database Tools (`listTables`, `getTableSchema`, `checkSql`, `executeQuery`) that call datasource services and never touch JDBC directly.
- Extend `AgentRequest` additively with optional `conversationId`, `datasourceId`, and `userId`. These values stay in server-controlled `ToolContext`; the model never generates `datasourceId`.
- Attach Database Tools and Database Agent system guidance only when a request carries `datasourceId`. Foundation date/calculator/system tools remain available.
- Keep `POST /api/v1/ai/chat` unchanged (`{message}` → `{content}`). Database Agent remains an internal upgrade of `POST /api/v1/ai/agent`.
- Enforce read-only SQL at both `checkSql` (agent flow) and `executeQuery` (system safety boundary), with max rows, query timeout, and result-size limits from configuration.
- Return structured tool errors to the model (no Java stack traces) so a failed query can be corrected in the same request.
- Record tool and SQL audit logs for who queried which datasource, with which SQL, and whether it succeeded. Query result rows are not persisted.
- Add unit, service, and scripted multi-step tests that do not call a real model.

## Capabilities

### New Capabilities

- `datasource-operations`: Named datasource registry, JDBC connection access, table/column metadata, and bounded read-only query execution.
- `database-sql-safety`: Parser-based SQL validation that allows only single-statement read-only queries and rejects DML/DDL, multi-statement, unparsable, oversized, or overly complex SQL.
- `database-agent-tools`: Spring AI tools `listTables`, `getTableSchema`, `checkSql`, and `executeQuery`, with structured results, structured failures, and server-controlled datasource context.
- `database-agent-execution`: Agent request context, Database Agent system prompt, native multi-step tool calling including SQL-error recovery, and SQL/tool audit logging.

### Modified Capabilities

None. Existing `ai-chat` request/response requirements stay as `{message}` → `{content}`. Foundation agent tools and `/api/v1/ai/agent` remain valid; new fields are optional and backward compatible.

## Impact

- `ai-datasource` gains production code, JSqlParser, and JDBC/Hikari dependencies.
- `ai-agent` depends on `ai-datasource` and registers Database Tools plus a database system prompt.
- `ai-server` wires datasource properties, database agent config, and optional named datasources.
- `POST /api/v1/ai/agent` accepts optional `conversationId`, `datasourceId`, and `userId` and may echo `conversationId` when present. Response `content` is unchanged.
- Default tests use H2 and a scripted `ChatModel`; no real credentials or network model calls.
- Out of scope: RAG, embeddings, business semantics, table search, cross-datasource joins, writes/DDL, conversation persistence, and user authentication.
