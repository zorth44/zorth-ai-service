package com.zorth.aiplatform.semantic.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.jspecify.annotations.Nullable;

public record TableRef(
        @JsonPropertyDescription("Visible physical table or CTE name; null for DERIVED and when UNKNOWN has only an alias.")
                @Nullable String table,
        @JsonPropertyDescription("Visible relation alias; required for DERIVED and otherwise null when absent.")
                @Nullable String alias,
        @JsonPropertyDescription("Relation classification: PHYSICAL, DERIVED, CTE, or UNKNOWN.") TableKind kind) {
}
