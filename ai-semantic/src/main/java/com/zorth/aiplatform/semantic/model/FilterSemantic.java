package com.zorth.aiplatform.semantic.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.jspecify.annotations.Nullable;

public record FilterSemantic(
        @JsonPropertyDescription("Complete fixed SQL predicate without surrounding XML.") String expression,
        @JsonPropertyDescription("Resolved physical table owner; null when absent, ambiguous, or derived.")
                @Nullable String table,
        @JsonPropertyDescription("Predicate column; null when the expression cannot be decomposed reliably.")
                @Nullable String column,
        @JsonPropertyDescription("SQL predicate operator; null when the expression cannot be decomposed reliably.")
                @Nullable String operator,
        @JsonPropertyDescription("Visible predicate value or parameter expression; null when not decomposable.")
                @Nullable String value,
        @JsonPropertyDescription("Grounding confidence from 0.0 to 1.0; visible SQL facts normally use 1.0.")
                double confidence) {
}
