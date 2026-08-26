package com.zorth.aiplatform.semantic.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

public record MapperStatementSemantic(
        @JsonPropertyDescription("Exact MyBatis statement id from the Mapper XML.") String id,
        @JsonPropertyDescription("Top-level SQL operation declared by the Mapper element.") SqlOperation operation,
        @JsonPropertyDescription("Concise factual description of the visible statement behavior.") String description,
        @JsonPropertyDescription("All physical, derived, CTE, or unknown relations referenced by the statement.")
                List<TableRef> tables,
        @JsonPropertyDescription("Columns visibly referenced by the statement; use UNKNOWN when usage is unclear.")
                List<ColumnRef> columns,
        @JsonPropertyDescription("Visible join relationships; use an empty array when none are present.")
                List<RelationshipSemantic> relationships,
        @JsonPropertyDescription("Non-dynamic SQL predicates; use an empty array when none are present.")
                List<FilterSemantic> fixedFilters,
        @JsonPropertyDescription("Predicates introduced by MyBatis dynamic tags; use an empty array when absent.")
                List<DynamicFilterSemantic> dynamicFilters,
        @JsonPropertyDescription("Visible GROUP BY expressions in source order; use an empty array when absent.")
                List<String> groupBy,
        @JsonPropertyDescription("Visible ORDER BY expressions in source order; use an empty array when absent.")
                List<String> orderBy,
        @JsonPropertyDescription("Supported non-trivial business inferences only; generic CRUD uses an empty array.")
                List<BusinessMeaning> businessMeanings,
        @JsonPropertyDescription("Grounding excerpts for the statement, including at least one factual item.")
                List<SemanticEvidence> evidence) {
}
