# Mapper Semantic Artifacts

## Purpose

Define the versioned Java/JSON contract, fact-versus-inference rules, confidence and evidence constraints, source provenance, and serialization guarantees for one MyBatis Mapper.

## Requirements

### Requirement: Versioned Mapper semantic artifact
The system SHALL represent one MyBatis Mapper as a typed `MapperSemantic` Java value containing non-blank `schemaVersion`, `sourceHash`, `mapperName`, `namespace`, and `sourceFile`; a concise `summary`; and a non-null list of statement semantics. Schema version `1.0` SHALL identify the initial contract, and `sourceHash` MUST be a lowercase SHA-256 digest of the original source-file bytes.

#### Scenario: Artifact carries compatible provenance
- **WHEN** semantic metadata is generated for `mapper/order/OrderMapper.xml`
- **THEN** the artifact contains schema version `1.0`, that normalized root-relative source path, and the SHA-256 digest of the processed file bytes

#### Scenario: Absolute server path is not exposed
- **WHEN** the configured source root is `/workspace/project/src/main/resources` and a Mapper is below that root
- **THEN** `sourceFile` contains only its normalized root-relative path and does not contain `/workspace/project/src/main/resources`

### Requirement: Typed statement semantic contract
Each `MapperStatementSemantic` SHALL contain a non-blank statement `id`, a non-null `SqlOperation`, a concise description, and non-null lists for tables, columns, relationships, fixed filters, dynamic filters, group-by expressions, order-by expressions, business meanings, and evidence. `SqlOperation` SHALL support `SELECT`, `INSERT`, `UPDATE`, `DELETE`, and `UNKNOWN`, and the artifact MUST represent every top-level MyBatis `select`, `insert`, `update`, and `delete` statement exactly once.

#### Scenario: Mixed Mapper preserves all operations
- **WHEN** a Mapper contains one top-level select, insert, update, and delete element
- **THEN** the artifact contains exactly four statement semantics with the matching ids and operations

#### Scenario: Empty semantic categories remain arrays
- **WHEN** a statement has no joins, dynamic filters, grouping, ordering, or business meanings
- **THEN** each corresponding JSON property is an empty array rather than null or absent

### Requirement: Typed table and column references
Tables SHALL be represented as `TableRef(table, alias)`. Columns SHALL be represented as `ColumnRef(table, column, alias, usage)`, where `usage` is a `ColumnUsage` value from `SELECT`, `JOIN`, `FILTER`, `GROUP_BY`, `ORDER_BY`, `UPDATE`, `INSERT`, and `UNKNOWN`. Unknown table ownership or aliases MUST be null rather than guessed, and unknown usage MUST be `UNKNOWN`.

#### Scenario: Qualified selected column is grounded
- **WHEN** SQL visibly selects `o.amount` from `t_order o`
- **THEN** the artifact can represent table `t_order`, column `amount`, and usage `SELECT` without inventing a column alias

#### Scenario: Ambiguous column ownership stays unknown
- **WHEN** an unqualified column cannot be assigned to one of several referenced tables from the Mapper XML alone
- **THEN** its `table` property is null

### Requirement: Relationships and filters preserve SQL facts
Relationships SHALL contain left and right table/column references, a `JoinType` from `INNER_JOIN`, `LEFT_JOIN`, `RIGHT_JOIN`, `FULL_JOIN`, `CROSS_JOIN`, and `UNKNOWN`, the source expression, and confidence. Fixed filters SHALL contain expression, optional table and column, operator, value, optional `possibleMeaning`, and confidence. Dynamic filters SHALL contain parameter, expression, optional table and column, operator, MyBatis condition, and confidence.

#### Scenario: Explicit left join is represented
- **WHEN** SQL contains `LEFT JOIN t_user u ON o.user_id = u.id`
- **THEN** the relationship identifies the two visible table columns, uses `LEFT_JOIN`, preserves the join expression, and has confidence `1.0`

#### Scenario: MyBatis if condition is represented separately
- **WHEN** an `<if test="startTime != null">` adds `o.create_time >= #{startTime}`
- **THEN** a dynamic filter records parameter `startTime`, the SQL expression, the MyBatis condition, and confidence `1.0`

### Requirement: Facts and inferred business meanings remain distinguishable
Directly visible tables, columns, relationships, and predicates SHALL be treated as facts and normally use confidence `1.0`. An inferred business interpretation MUST be represented by `BusinessMeaning(name, description, derivedFrom, confidence)`, MUST cite its derivation, and MUST have confidence of at least `0.7`. A fixed filter's non-null `possibleMeaning` MUST have a corresponding business meaning carrying the inference evidence and confidence. The system MUST NOT assign undocumented meanings to status codes, enum-like values, relationships, or business rules.

#### Scenario: Literal predicate remains a fact without invented meaning
- **WHEN** a Mapper contains `status = '03'` with no supporting name or comment explaining `03`
- **THEN** the fixed filter preserves the literal predicate with confidence `1.0` and emits no claim that `03` means completed, normal, or any other business state

#### Scenario: Supported inference is separated
- **WHEN** a statement id and predicate jointly support a likely business interpretation at confidence `0.8`
- **THEN** the predicate remains a factual filter and the interpretation appears separately with `derivedFrom` and confidence `0.8`

#### Scenario: Weak inference is rejected
- **WHEN** structured output contains a `BusinessMeaning` whose confidence is below `0.7`
- **THEN** local artifact validation rejects the Mapper semantic result

### Requirement: Statement evidence is mandatory and grounded
Every statement SHALL contain at least one `SemanticEvidence` with the trusted source file, the containing statement id, an `EvidenceType`, and a concise evidence excerpt. `EvidenceType` SHALL distinguish SQL, dynamic XML, local SQL fragment, inference, and unresolved include evidence. Evidence MUST be present for important joins, predicates, dynamic conditions, business inferences, and unresolved fragment references, and MUST NOT contain an invented source file or unrelated statement id.

#### Scenario: Join evidence is retained
- **WHEN** a statement semantic contains an extracted join relationship
- **THEN** its evidence includes the relevant join expression and identifies the same source file and statement id

#### Scenario: Unresolved include is explicit
- **WHEN** a statement references an include that cannot be resolved inside the same Mapper XML
- **THEN** evidence identifies the unresolved reference and no fragment content is invented

### Requirement: Strict artifact validation
The system MUST reject a semantic result with a blank required scalar, null required collection, missing or duplicate expected statement, mismatched namespace or operation, confidence outside `[0.0, 1.0]`, invalid business-inference threshold, evidence pointing outside the trusted statement inventory, or server provenance inconsistent with the processed file. It MUST NOT silently convert a missing model-produced collection to an empty list.

#### Scenario: Missing statement fails validation
- **WHEN** the source preflight finds statement ids `findOne` and `findAll` but structured output contains only `findOne`
- **THEN** local validation fails instead of writing a partial semantic artifact

#### Scenario: Null collection fails validation
- **WHEN** structured output deserializes with a null `relationships` collection
- **THEN** local validation fails instead of normalizing it to an empty array

#### Scenario: Trusted provenance wins
- **WHEN** model output echoes a different source path, source hash, or evidence source path
- **THEN** the generated artifact uses only application-computed provenance and validation prevents an inconsistent artifact from being persisted

### Requirement: Jackson round-trip compatibility
The system SHALL serialize semantic artifacts as UTF-8 JSON using the application Jackson configuration and MUST deserialize every successfully generated file back into an equivalent `MapperSemantic` value before publication. The artifact contract MUST NOT expose Spring AI response types, provider-specific types, or arbitrary `Map<String, Object>` payloads.

#### Scenario: Generated JSON round trips
- **WHEN** a valid `MapperSemantic` is pretty-printed to a candidate output file
- **THEN** Jackson reads that file back into an equivalent typed value before it becomes the final output

#### Scenario: Malformed generated JSON is not published
- **WHEN** serialization or read-back does not produce an equivalent `MapperSemantic`
- **THEN** the candidate is treated as a write failure and is not published at the target path
