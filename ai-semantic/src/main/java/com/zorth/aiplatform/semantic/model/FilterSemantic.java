package com.zorth.aiplatform.semantic.model;

public record FilterSemantic(
        String expression,
        String table,
        String column,
        String operator,
        String value,
        String possibleMeaning,
        double confidence) {
}
