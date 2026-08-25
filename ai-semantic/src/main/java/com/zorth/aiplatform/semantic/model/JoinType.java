package com.zorth.aiplatform.semantic.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum JoinType {
    INNER_JOIN,
    LEFT_JOIN,
    RIGHT_JOIN,
    FULL_JOIN,
    CROSS_JOIN,
    UNKNOWN;

    @JsonValue
    public String jsonValue() {
        return name();
    }
}
