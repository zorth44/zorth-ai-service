package com.zorth.aiplatform.semantic.model;

import java.util.ArrayList;
import java.util.List;

public record MapperSemantic(
        String schemaVersion,
        String sourceHash,
        String mapperName,
        String namespace,
        String sourceFile,
        String summary,
        List<MapperStatementSemantic> statements) {

    public static final String SCHEMA_VERSION = "1.0";

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
