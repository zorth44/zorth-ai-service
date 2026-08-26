## 1. Web-sql allowlist

- [x] 1.1 Change `WebSqlSettings.allows` so an empty `allowedDatasourceIds` list permits any present `datasourceId`, and a non-empty list still requires membership.
- [x] 1.2 Update `WebSqlAdaptersTest` so empty allowlist plus complete context is not `DATASOURCE_NOT_ALLOWED`, unlisted IDs still fail when the list is set, and missing `database` / Authorization still skip HTTP.
- [x] 1.3 Add a comment on `ai.datasource.web-sql.allowed-datasource-ids` in `application.yml` that empty means unrestricted.

## 2. Database Copilot prompt

- [x] 2.1 Update `database-agent-system-prompt.txt` to require Markdown `sql` fences, SQL-first answers, no result-grid dumps, full repaired statements, and optional read-only `executeQuery` verification.
- [x] 2.2 Add a prompt-shape test that the Database prompt resource (or the prompt attached when `datasourceId` is present) contains the fence, no-grid, full-repair, and verification rules.
- [x] 2.3 Keep existing Database Agent attachment tests passing (`Database Tools`, numeric-string warning, message-only Agent unchanged).

## 3. Docs

- [x] 3.1 Update `docs/local-web-sql.md` and `scripts/smoke-database-agent.sh` so default local start does not require `allowed-datasource-ids`; document the property as an optional lock and Token as authorization.

## 4. Verification

- [x] 4.1 Run the affected unit tests (`ai-datasource` web-sql adapter tests, `ai-agent` prompt and Database Agent tests) and fix regressions.
