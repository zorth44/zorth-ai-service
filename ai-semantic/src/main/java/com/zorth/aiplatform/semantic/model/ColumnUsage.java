package com.zorth.aiplatform.semantic.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ColumnUsage {
    SELECT,
    JOIN,
    FILTER,
    GROUP_BY,
    ORDER_BY,
    UPDATE,
    INSERT,
    UNKNOWN;

    @JsonValue
    public String jsonValue() {
        return name();
    }
}
