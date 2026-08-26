package com.zorth.aiplatform.semantic.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.jspecify.annotations.Nullable;

public record ColumnRef(
        @JsonPropertyDescription("Resolved visible physical table owner; null when ownership is ambiguous or derived.")
                @Nullable String table,
        @JsonPropertyDescription("Visible column name or expression identifier; never blank.") String column,
        @JsonPropertyDescription("Visible column alias; null when the SQL has no alias.") @Nullable String alias,
        @JsonPropertyDescription("How the statement uses the column, or UNKNOWN when not determinable.")
                ColumnUsage usage) {
}
