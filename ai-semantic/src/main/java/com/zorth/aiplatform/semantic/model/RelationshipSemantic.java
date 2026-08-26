package com.zorth.aiplatform.semantic.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.jspecify.annotations.Nullable;

public record RelationshipSemantic(
        @JsonPropertyDescription("Resolved physical table on the left; null when the side is derived or unknown.")
                @Nullable String leftTable,
        @JsonPropertyDescription("Visible left-side column, including the source alias when needed.") String leftColumn,
        @JsonPropertyDescription("Resolved physical table on the right; null when the side is derived or unknown.")
                @Nullable String rightTable,
        @JsonPropertyDescription("Visible right-side column, including the source alias when needed.") String rightColumn,
        @JsonPropertyDescription("Visible SQL join type, or UNKNOWN when the source does not determine it.")
                JoinType joinType,
        @JsonPropertyDescription("SQL join predicate without surrounding XML, for example o.user_id = u.id.")
                String expression,
        @JsonPropertyDescription("Grounding confidence from 0.0 to 1.0; visible SQL facts normally use 1.0.")
                double confidence) {
}
