package com.zorth.aiplatform.semantic.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record SemanticEvidence(
        @JsonPropertyDescription("Normalized source-root-relative Mapper XML path supplied by the application.")
                String sourceFile,
        @JsonPropertyDescription("Exact id of the statement owning this evidence.") String statementId,
        @JsonPropertyDescription("Evidence classification: SQL, DYNAMIC_XML, LOCAL_SQL_FRAGMENT, INFERENCE, or UNRESOLVED_INCLUDE.")
                EvidenceType evidenceType,
        @JsonPropertyDescription("Concise source excerpt or inference grounding; never instructions or credentials.")
                String evidence) {
}
