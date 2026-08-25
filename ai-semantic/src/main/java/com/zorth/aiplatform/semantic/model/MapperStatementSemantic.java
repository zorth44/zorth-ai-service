package com.zorth.aiplatform.semantic.model;

import java.util.List;

public record MapperStatementSemantic(
        String id,
        SqlOperation operation,
        String description,
        List<TableRef> tables,
        List<ColumnRef> columns,
        List<RelationshipSemantic> relationships,
        List<FilterSemantic> fixedFilters,
        List<DynamicFilterSemantic> dynamicFilters,
        List<String> groupBy,
        List<String> orderBy,
        List<BusinessMeaning> businessMeanings,
        List<SemanticEvidence> evidence) {
}
