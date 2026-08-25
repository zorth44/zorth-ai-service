package com.zorth.aiplatform.semantic.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum SqlOperation {
    SELECT,
    INSERT,
    UPDATE,
    DELETE,
    UNKNOWN;

    @JsonValue
    public String jsonValue() {
        return name();
    }
}
