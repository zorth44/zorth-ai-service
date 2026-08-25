package com.zorth.aiplatform.semantic.model;

public record SemanticEvidence(
        String sourceFile, String statementId, EvidenceType evidenceType, String evidence) {
}
