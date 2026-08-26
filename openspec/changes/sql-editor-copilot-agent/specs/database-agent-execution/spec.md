## MODIFIED Requirements

### Requirement: Database system prompt without a forced sequence
When `datasourceId` is present, the system SHALL add Database Agent guidance: discover tables if needed, inspect relevant schema, generate read-only SQL, validate, optionally execute to verify, repair failed SQL, and answer with insertable SQL. The prompt MUST NOT require every question to call every tool.

#### Scenario: Guidance is attached for database requests
- **WHEN** the agent executes a request that includes `datasourceId`
- **THEN** the system prompt includes read-only Database Agent instructions

#### Scenario: Foundation requests keep the original prompt
- **WHEN** the agent executes a request without `datasourceId`
- **THEN** Database Tools are not registered and the original foundation prompt is used

## ADDED Requirements

### Requirement: Copilot SQL answer shape
When `datasourceId` is present, Database Agent guidance MUST require proposed or repaired SQL in Markdown fenced code blocks tagged `sql`, one complete statement per block. The user-facing answer MUST lead with those blocks and a short explanation of what the statement does, whether it was verified with `executeQuery`, and an approximate row count when a verification query ran. The prompt MUST forbid pasting query result rows into `content`. Repair answers MUST give the full corrected statement, not a diff fragment. The prompt MUST still allow read-only `executeQuery` for verification and same-request repair.

#### Scenario: Prompt requires sql fences
- **WHEN** the agent executes a request that includes `datasourceId`
- **THEN** the attached system prompt tells the model to put SQL in Markdown `sql` fences and not to paste result grids into the answer

#### Scenario: Prompt allows verification queries
- **WHEN** the agent executes a request that includes `datasourceId`
- **THEN** the attached system prompt still allows `executeQuery` for read-only verification and repair
