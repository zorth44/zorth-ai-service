package com.zorth.aiplatform.semantic.model;

public record DynamicFilterSemantic(
        String parameter,
        String expression,
        String table,
        String column,
        String operator,
        String condition,
        double confidence) {
}
