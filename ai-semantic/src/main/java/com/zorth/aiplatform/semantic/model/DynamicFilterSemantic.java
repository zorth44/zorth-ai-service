package com.zorth.aiplatform.semantic.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.jspecify.annotations.Nullable;

public record DynamicFilterSemantic(
        @JsonPropertyDescription("MyBatis parameter or collection name, for example startTime or itemIds.")
                String parameter,
        @JsonPropertyDescription("Conditional SQL fragment only, without an enclosing dynamic XML tag; for example o.created_at >= #{startTime}.")
                String expression,
        @JsonPropertyDescription("Resolved physical table owner; null when absent, ambiguous, or derived.")
                @Nullable String table,
        @JsonPropertyDescription("Predicate column; null when the SQL fragment cannot be decomposed reliably.")
                @Nullable String column,
        @JsonPropertyDescription("SQL predicate operator; null when the SQL fragment cannot be decomposed reliably.")
                @Nullable String operator,
        @JsonPropertyDescription("MyBatis/OGNL guard only, without SQL or an enclosing XML tag; for example startTime != null.")
                String condition,
        @JsonPropertyDescription("Grounding confidence from 0.0 to 1.0; visible dynamic XML normally uses 1.0.")
                double confidence) {
}
