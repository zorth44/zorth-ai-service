## 1. Module and Configuration Foundation

- [x] 1.1 Add JDBC, Hikari, JSqlParser, and test-scoped H2 dependencies to `ai-datasource`; depend on `ai-datasource` from `ai-agent` and `ai-server`.
- [x] 1.2 Add typed `ai.datasources` registry properties and `ai.agent.database` limits (`max-rows`, `query-timeout-seconds`, `max-result-bytes`, `max-sql-length`, `include-views`) with non-secret defaults.
- [x] 1.3 Implement a named datasource registry that lazily creates Hikari pools, rejects unknown ids, and closes pools on shutdown.

## 2. Datasource Services

- [x] 2.1 Implement `DatabaseMetadataService` using JDBC `DatabaseMetaData` for `TABLE` listing and structured table/column/primary-key schema.
- [x] 2.2 Implement `SqlValidationService` with JSqlParser: allow single SELECT/WITH, reject writes/DDL/`SELECT INTO`, multi-statement, empty, unparsable, oversized, and overly complex SQL.
- [x] 2.3 Implement `QueryExecutionService` that re-validates SQL, applies dialect row limits, query timeout, max result size, JSON-safe row conversion, and `QueryResult` truncation flags.

## 3. Database Tools and Agent Runtime

- [x] 3.1 Add `ToolContext` keys and a helper that reads `requestId`, `conversationId`, `userId`, and `datasourceId` without exposing them in tool schemas.
- [x] 3.2 Implement `DatabaseTools` (`listTables`, `getTableSchema`, `checkSql`, `executeQuery`) that call datasource services, return structured results or `{success,errorType,message}`, and never use JDBC types.
- [x] 3.3 Add database-tool audit logging for conversation, user, datasource, tool name, SQL/table arguments, status, duration, row count, and error message.
- [x] 3.4 Extend `AgentRequest`/`AgentResponse` additively and update `SpringAiAgentService` to put context in `ToolContext`, attach Database Tools and the database system prompt only when `datasourceId` is present, and keep the Spring AI advisor loop.

## 4. Server Wiring and Compatibility

- [x] 4.1 Register datasource and database-tool beans in server configuration without changing `/api/v1/ai/chat`.
- [x] 4.2 Add the Database Agent system prompt resource covering discovery, schema, validate, execute, repair, and read-only rules without a forced full-tool sequence.
- [x] 4.3 Update README for optional agent context fields, datasource configuration, Database Tools, audit logs, and opt-in real-model cases.

## 5. Tests

- [x] 5.1 Add H2-backed unit tests for metadata listing/schema, SQL accept/reject cases, query limits, timeout configuration, and direct `executeQuery` write blocking.
- [x] 5.2 Add Database Tool schema tests proving server context keys are absent, plus tool tests for success and structured errors.
- [x] 5.3 Add scripted multi-step tests for `listTables → getTableSchema → checkSql → executeQuery` and for SQL-error repair without an application loop.
- [x] 5.4 Extend agent service and controller tests for optional context fields, message-only compatibility, and unchanged chat behavior; run `mvn clean test`.
