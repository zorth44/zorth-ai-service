package com.zorth.aiplatform.semantic.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TableKind {
    PHYSICAL,
    DERIVED,
    CTE,
    UNKNOWN;

    @JsonValue
    public String jsonValue() {
        return name();
    }
}
