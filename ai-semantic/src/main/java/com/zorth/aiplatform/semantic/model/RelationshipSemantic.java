package com.zorth.aiplatform.semantic.model;

public record RelationshipSemantic(
        String leftTable,
        String leftColumn,
        String rightTable,
        String rightColumn,
        JoinType joinType,
        String expression,
        double confidence) {
}
