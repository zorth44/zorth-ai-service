## ADDED Requirements

### Requirement: Model-visible semantic schema is described
Every record component exposed to Spring AI structured output SHALL carry a concise generated-schema description that states its semantic meaning, expected representation, and nullability. The generated schema MUST distinguish required components from optional components using annotations supported by the pinned Spring AI version, and tests MUST inspect the same generated schema path used by production entity conversion.

#### Scenario: Dynamic fields have distinct descriptions
- **WHEN** the JSON Schema is generated for `DynamicFilterSemantic`
- **THEN** `expression` is described as SQL without an enclosing dynamic XML tag and `condition` is described as the MyBatis/OGNL guard without SQL

#### Scenario: Optional alias is not required
- **WHEN** the JSON Schema is generated for a table or column alias annotated as nullable
- **THEN** the alias is absent from that object's required-property list while required identifiers remain present

#### Scenario: Every structured property is documented
- **WHEN** the complete `MapperSemantic` JSON Schema and referenced definitions are inspected
- **THEN** every model-visible property has a non-blank description

## MODIFIED Requirements

### Requirement: Versioned Mapper semantic artifact
The system SHALL represent one MyBatis Mapper as a typed `MapperSemantic` Java value containing non-blank `schemaVersion`, `sourceHash`, `mapperName`, `namespace`, and `sourceFile`; a concise `summary`; and a non-null list of statement semantics. Schema version `1.1` SHALL identify the stabilized contract, and `sourceHash` MUST be a lowercase SHA-256 digest of the original source-file bytes. Schema 1.0 artifacts MUST be regenerated rather than silently accepted as schema 1.1.

#### Scenario: Artifact carries compatible provenance
- **WHEN** semantic metadata is generated for `mapper/order/OrderMapper.xml`
- **THEN** the artifact contains schema version `1.1`, that normalized root-relative source path, and the SHA-256 digest of the processed file bytes

#### Scenario: Absolute server path is not exposed
- **WHEN** the configured source root is `/workspace/project/src/main/resources` and a Mapper is below that root
- **THEN** `sourceFile` contains only its normalized root-relative path and does not contain `/workspace/project/src/main/resources`

#### Scenario: Version 1.0 is not relabeled
- **WHEN** an existing schema 1.0 artifact is encountered after this change
- **THEN** it is not treated as a valid schema 1.1 artifact and must be regenerated from its Mapper source

### Requirement: Typed table and column references
Relations SHALL be represented as `TableRef(table, alias, kind)`, where `kind` is a `TableKind` value from `PHYSICAL`, `DERIVED`, `CTE`, and `UNKNOWN`. A physical relation MUST contain its visible database table and MAY have a null alias. A derived relation MUST contain a null table and its visible alias. A CTE MUST contain its visible CTE name and MAY have a null alias. An unknown relation MUST preserve at least one visible source token without inventing a descriptive identifier. Columns SHALL be represented as `ColumnRef(table, column, alias, usage)`, where optional table ownership and alias MAY be null and `usage` is a `ColumnUsage` value from `SELECT`, `JOIN`, `FILTER`, `GROUP_BY`, `ORDER_BY`, `UPDATE`, `INSERT`, and `UNKNOWN`. Unknown values MUST be null or `UNKNOWN` as specified and MUST NOT be empty strings.

#### Scenario: Qualified selected column is grounded
- **WHEN** SQL visibly selects `o.amount` from `t_order o`
- **THEN** the artifact represents `t_order` as `PHYSICAL`, column `amount` with table `t_order`, usage `SELECT`, and no invented column alias

#### Scenario: Ambiguous column ownership stays unknown
- **WHEN** an unqualified column cannot be assigned to one of several referenced tables from the Mapper XML alone
- **THEN** its `table` property is null rather than an empty or guessed string

#### Scenario: UNION subquery is a derived relation
- **WHEN** SQL contains `FROM (...) bn JOIN tasks t ON t.id = bn.task_id`
- **THEN** the relation list contains `(table=null, alias=bn, kind=DERIVED)` and `(table=tasks, alias=t, kind=PHYSICAL)` and does not invent a table named `derived_union`

### Requirement: Relationships and filters preserve SQL facts
Relationships SHALL contain nullable left and right physical table names, visible left and right columns, a `JoinType` from `INNER_JOIN`, `LEFT_JOIN`, `RIGHT_JOIN`, `FULL_JOIN`, `CROSS_JOIN`, and `UNKNOWN`, the source expression, and confidence. A derived side MAY have a null physical table name when its alias remains available through a `DERIVED` `TableRef`, the relationship expression, and evidence. Fixed filters SHALL contain expression, optional table and column, optional operator and value, and confidence; they SHALL NOT contain business meaning. Dynamic filters SHALL contain parameter, SQL expression, optional table and column, optional operator, MyBatis/OGNL condition, and confidence. Optional values MUST be null rather than empty strings.

#### Scenario: Explicit left join is represented
- **WHEN** SQL contains `LEFT JOIN t_user u ON o.user_id = u.id`
- **THEN** the relationship identifies the two visible physical table columns, uses `LEFT_JOIN`, preserves the join expression, and has confidence `1.0`

#### Scenario: MyBatis if condition is represented separately
- **WHEN** an `<if test="startTime != null">` adds `o.create_time >= #{startTime}`
- **THEN** the dynamic filter has parameter `startTime`, expression `o.create_time >= #{startTime}`, condition `startTime != null`, and confidence `1.0`, with no enclosing XML tag in either field

#### Scenario: Complex predicate can omit scalar decomposition
- **WHEN** a fixed predicate is visible but cannot be reduced reliably to one table, column, operator, or value
- **THEN** its expression remains present and uncertain optional components are null instead of empty or invented strings

### Requirement: Facts and inferred business meanings remain distinguishable
Directly visible tables, columns, relationships, and predicates SHALL be treated as facts and normally use confidence `1.0`. All inferred business interpretation MUST be represented only by `BusinessMeaning(name, description, derivedFrom, confidence)` with corresponding `INFERENCE` evidence, and its confidence MUST be at least `0.7`. A business meaning MUST add a supported non-trivial interpretation and MUST NOT merely paraphrase the statement id, SQL operation, description, or generic CRUD purpose. Inference based only on a statement id and predicate MUST have confidence below `0.9`; confidence of `0.9` or greater requires explicit strong code evidence. The system MUST NOT assign undocumented meanings to status codes, enum-like values, relationships, or business rules.

#### Scenario: Literal predicate remains a fact without invented meaning
- **WHEN** a Mapper contains `status = '03'` with no supporting name or comment explaining `03`
- **THEN** the fixed filter preserves the literal predicate with confidence `1.0` and emits no claim that `03` means completed, normal, or any other business state

#### Scenario: Supported inference is separated
- **WHEN** a statement id and predicate jointly support a non-trivial likely business interpretation at confidence `0.8`
- **THEN** the predicate remains a factual filter and the interpretation appears once in `businessMeanings` with `derivedFrom`, confidence `0.8`, and inference evidence

#### Scenario: Generic CRUD has no business meaning
- **WHEN** an insert statement only shows that a row is created and supplies no further business rule
- **THEN** its `businessMeanings` list is empty rather than containing a high-confidence paraphrase such as `Create item`

#### Scenario: Weak inference is rejected
- **WHEN** structured output contains a `BusinessMeaning` whose confidence is below `0.7`
- **THEN** local artifact validation rejects the Mapper semantic result

### Requirement: Strict artifact validation
The system MUST reject a semantic result with a blank required scalar, an empty-string optional scalar, null required collection, missing or duplicate expected statement, mismatched namespace or operation, invalid `TableKind` shape, dynamic expression/condition reversal, confidence outside `[0.0, 1.0]`, invalid business-inference threshold, evidence pointing outside the trusted statement inventory, or server provenance inconsistent with the processed file. It MUST permit annotated optional scalars to be null and MUST NOT silently convert a missing model-produced collection to an empty list.

#### Scenario: Missing statement fails validation
- **WHEN** the source preflight finds statement ids `findOne` and `findAll` but structured output contains only `findOne`
- **THEN** local validation fails instead of writing a partial semantic artifact

#### Scenario: Null collection fails validation
- **WHEN** structured output deserializes with a null `relationships` collection
- **THEN** local validation fails instead of normalizing it to an empty array

#### Scenario: Nullable alias succeeds
- **WHEN** a physical table has no visible alias and structured output leaves its nullable alias absent or null
- **THEN** local validation accepts null and the published artifact does not replace it with an empty string

#### Scenario: Empty optional string fails validation
- **WHEN** structured output uses `""` for an absent alias, table owner, operator, or value
- **THEN** local validation rejects the result rather than publishing ambiguous absence

#### Scenario: Trusted provenance wins
- **WHEN** model output echoes a different source path, source hash, or evidence source path
- **THEN** the generated artifact uses only application-computed provenance and validation prevents an inconsistent artifact from being persisted
