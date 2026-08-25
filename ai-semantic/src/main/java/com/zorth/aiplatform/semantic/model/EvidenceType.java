package com.zorth.aiplatform.semantic.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum EvidenceType {
    SQL,
    DYNAMIC_XML,
    LOCAL_SQL_FRAGMENT,
    INFERENCE,
    UNRESOLVED_INCLUDE;

    @JsonValue
    public String jsonValue() {
        return name();
    }
}
