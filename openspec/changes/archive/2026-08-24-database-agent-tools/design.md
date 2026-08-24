## Context

Phase 2 activated `ai-agent` with Spring AI 2.0 `ChatClient` tool calling, `ToolCallingAdvisor`, `ToolContext`, and foundation date/calculator/system tools. `POST /api/v1/ai/chat` remains a message-only chatbot. `ai-datasource` is an empty Maven module. There is no JDBC, SQL parser, conversation persistence, or authentication.

The product request is to replace prompt-only Text-to-SQL with a Database Agent that uses LangChain `SQLDatabaseToolkit` equivalents. The existing project already forbids a custom agent loop and forbids exposing `datasourceId` as a model-visible tool argument. Those constraints stand.

Because no datasource or SQL layer exists, this change implements the minimum foundation required for the tools. It does not invent a new agent framework.

## Goals / Non-Goals

**Goals:**

- Give the model four database tools and let Spring AI own multi-step calling.
- Keep JDBC behind datasource services; tools only call those services.
- Read `datasourceId`, `userId`, and `conversationId` from server-controlled context.
- Allow only single-statement read-only SQL, even if the model skips `checkSql`.
- Bound query rows, timeout, and result size from configuration.
- Return structured tool errors so the model can repair SQL in the same request.
- Keep `/api/v1/ai/chat` and existing `{message}`-only agent clients working.

**Non-Goals:**

- RAG, embeddings, business semantics, table search, or relation discovery.
- Writes, DDL, approvals, or a database-modification agent.
- Conversation persistence, query-result persistence, or a real auth system.
- Cross-datasource joins or a dynamic tool marketplace.
- Changing chat response `content` to `message`.

## Decisions

### 1. Put JDBC in `ai-datasource`; put `@Tool` wrappers in `ai-agent`

`ai-datasource` owns the registry, metadata, SQL validation, and query execution and has no Spring AI dependency. `DatabaseTools` lives in `ai-agent` and depends on those services. Putting tools in `ai-server` was rejected because it would mix HTTP bootstrap with reusable agent capabilities. Putting `@Tool` in `ai-datasource` was rejected because that module should stay provider-neutral and free of chat types.

### 2. Keep Database Agent on `/api/v1/ai/agent`, not `/api/v1/ai/chat`

Chat stays `{message}` → `{content}` with no tools. Agent already owns tool calling. `AgentRequest` gains optional `conversationId`, `datasourceId`, and `userId`, plus a one-argument constructor so existing Java callers keep compiling. When `conversationId` is present, `AgentResponse` may echo it; `content` remains the answer field.

Routing chat into the agent when `datasourceId` is present was rejected: it would silently change chat semantics and mix two contracts. The request document assumed chat already had those fields; it does not, and changing `content` to `message` would be breaking.

### 3. Attach Database Tools only when `datasourceId` is present

Foundation tools stay on every agent request. Database Tools and the database system prompt are added only when the request includes a datasource. Always registering SQL tools would invite the model to call `listTables` for unrelated questions.

### 4. Reuse `ToolContext` instead of a ThreadLocal execution context

`SpringAiAgentService` already generates `requestId` and puts it in `ToolContext`. This change adds `conversationId`, `userId`, and `datasourceId` under shared keys. Tools read them through a small helper. A ThreadLocal `AgentExecutionContext` was rejected because Tomcat/Hikari worker threads can leak values and Spring AI already injects `ToolContext`.

### 5. Validate SQL with JSqlParser, twice

There is no existing parser. JSqlParser is used to require a single `SELECT` (including `WITH`), reject DML/DDL and `SELECT INTO`, reject multi-statement input, and enforce length/complexity limits. A `startsWith("select")` check is not sufficient.

`checkSql` is the agent-facing validator. `executeQuery` calls the same validator before touching JDBC. The second check is the system safety boundary.

### 6. Return structured failures from database tools; do not fail the whole request

Foundation tools throw `ToolExecutionException`, which currently becomes an HTTP error. Database SQL mistakes must stay inside the tool-calling loop so the model can call `getTableSchema` and retry. Database tools therefore catch validation and SQL errors, log the full exception server-side, and return `{success:false, errorType, message}` to the model.

### 7. Limit results in the executor, not in the prompt

Configuration:

```yaml
ai.agent.database:
  max-rows: 200
  query-timeout-seconds: 10
  max-result-bytes: 1048576
  max-sql-length: 10000
  include-views: false
```

If the SQL has no limit, the executor adds a dialect-appropriate limit (`LIMIT`, `FETCH FIRST`, or `TOP`). `Statement.setQueryTimeout` and `setMaxRows` are also applied. Oversized results set `truncated=true` and a short message. Rows are converted to JSON-safe scalars; JDBC objects are never returned to the model.

### 8. Named YAML datasources and Hikari pools, created lazily

Datasources are declared under `ai.datasources.<id>` with JDBC URL, username, password, and optional driver. Pools are created on first use and closed on shutdown. The application starts with an empty registry. Tests use H2. Production drivers stay out of the default classpath.

### 9. Audit SQL in a dedicated logger; do not persist rows

`ToolExecutionSupport` continues to log request ID, tool name, duration, and status without generic argument dumps. A database audit logger additionally records `conversationId`, `userId`, `datasourceId`, tool name, SQL or table-name arguments, status, duration, row count, and error message. Result rows are not written to storage in this phase.

## Risks / Trade-offs

- [No auth means `userId` is a client-supplied optional field] → Treat it as correlation only; do not claim it is authenticated identity.
- [H2 metadata and comments differ from MySQL/Postgres] → Use JDBC `DatabaseMetaData` and document dialect-specific comment coverage.
- [JSqlParser may reject unusual vendor SQL that is still read-only] → Fail closed and return a structured parse error; do not fall back to string matching.
- [Wrapping SQL to add LIMIT can break some statements] → Prefer parser mutation of the existing SELECT; if that fails, reject rather than execute unbounded.
- [Returning tool errors instead of throwing changes exception semantics for DB tools only] → Keep foundation-tool failure behavior unchanged.
- [Large schemas still overflow context if the model lists every table] → Accept this MVP limit; `searchTables` is explicitly deferred.

## Migration Plan

No data migration. Add module dependencies, configuration, tools, and tests. Existing chat and message-only agent clients keep working. Rollback removes the new datasource module code, Database Tools, and optional agent fields; no persistent state needs restoration.

## Open Questions

None blocking. Conversation persistence and authenticated user identity remain later changes.
