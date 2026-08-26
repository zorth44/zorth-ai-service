package com.zorth.aiplatform.semantic.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.ArrayList;
import java.util.List;

public record MapperSemantic(
        @JsonPropertyDescription("Artifact contract version; must be 1.1.") String schemaVersion,
        @JsonPropertyDescription("Lowercase SHA-256 digest of the original Mapper XML bytes.") String sourceHash,
        @JsonPropertyDescription("Mapper file name without the .xml suffix, for example OrderMapper.") String mapperName,
        @JsonPropertyDescription("Exact namespace declared by the Mapper XML root element.") String namespace,
        @JsonPropertyDescription("Normalized source-root-relative Mapper XML path; never an absolute path.")
                String sourceFile,
        @JsonPropertyDescription("Concise factual summary of the Mapper without unsupported business inference.")
                String summary,
        @JsonPropertyDescription("Semantics for every top-level select, insert, update, and delete exactly once.")
                List<MapperStatementSemantic> statements) {

    public static final String SCHEMA_VERSION = "1.1";

    public MapperSemantic withTrustedProvenance(String sourceHash, String sourceFile) {
        List<MapperStatementSemantic> rewritten = new ArrayList<>();
        if (statements != null) {
            for (MapperStatementSemantic statement : statements) {
                rewritten.add(rewriteEvidenceSource(statement, sourceFile));
            }
        }
        return new MapperSemantic(
                SCHEMA_VERSION,
                sourceHash,
                mapperName,
                namespace,
                sourceFile,
                summary,
                statements == null ? null : List.copyOf(rewritten));
    }

    private static MapperStatementSemantic rewriteEvidenceSource(
            MapperStatementSemantic statement, String trustedSourceFile) {
        if (statement == null || statement.evidence() == null) {
            return statement;
        }
        List<SemanticEvidence> evidence = new ArrayList<>();
        for (SemanticEvidence item : statement.evidence()) {
            if (item == null) {
                evidence.add(null);
                continue;
            }
            evidence.add(new SemanticEvidence(
                    trustedSourceFile, item.statementId(), item.evidenceType(), item.evidence()));
        }
        return new MapperStatementSemantic(
                statement.id(),
                statement.operation(),
                statement.description(),
                statement.tables(),
                statement.columns(),
                statement.relationships(),
                statement.fixedFilters(),
                statement.dynamicFilters(),
                statement.groupBy(),
                statement.orderBy(),
                statement.businessMeanings(),
                List.copyOf(evidence));
    }
}
