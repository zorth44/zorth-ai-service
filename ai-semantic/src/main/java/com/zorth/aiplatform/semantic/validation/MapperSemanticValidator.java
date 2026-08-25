package com.zorth.aiplatform.semantic.validation;

import com.zorth.aiplatform.semantic.exception.MapperSemanticExtractionException;
import com.zorth.aiplatform.semantic.model.BusinessMeaning;
import com.zorth.aiplatform.semantic.model.ColumnRef;
import com.zorth.aiplatform.semantic.model.DynamicFilterSemantic;
import com.zorth.aiplatform.semantic.model.FilterSemantic;
import com.zorth.aiplatform.semantic.model.MapperSemantic;
import com.zorth.aiplatform.semantic.model.MapperStatementSemantic;
import com.zorth.aiplatform.semantic.model.RelationshipSemantic;
import com.zorth.aiplatform.semantic.model.SemanticEvidence;
import com.zorth.aiplatform.semantic.model.TableRef;
import com.zorth.aiplatform.semantic.report.MapperSemanticFailureType;
import com.zorth.aiplatform.semantic.scan.MapperPreflightResult;
import com.zorth.aiplatform.semantic.scan.SourcePaths;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class MapperSemanticValidator {

    public void validate(
            MapperSemantic semantic,
            String trustedSourceHash,
            String trustedSourceFile,
            String trustedMapperName,
            MapperPreflightResult preflight) {
        Objects.requireNonNull(preflight, "preflight must not be null");
        if (semantic == null) {
            reject("Mapper semantic result is missing");
        }
        requireText(semantic.schemaVersion(), "schemaVersion");
        if (!MapperSemantic.SCHEMA_VERSION.equals(semantic.schemaVersion())) {
            reject("schemaVersion is not the supported contract");
        }
        requireText(semantic.sourceHash(), "sourceHash");
        requireText(semantic.mapperName(), "mapperName");
        requireText(semantic.namespace(), "namespace");
        requireText(semantic.sourceFile(), "sourceFile");
        requireText(semantic.summary(), "summary");
        if (semantic.statements() == null) {
            reject("statements must not be null");
        }
        if (!trustedSourceHash.equals(semantic.sourceHash())
                || !trustedSourceFile.equals(semantic.sourceFile())) {
            reject("Provenance does not match the processed Mapper file");
        }
        if (!trustedMapperName.equals(semantic.mapperName())) {
            reject("mapperName does not match the processed Mapper file");
        }
        if (!preflight.namespace().equals(semantic.namespace())) {
            reject("namespace does not match the Mapper XML");
        }

        Map<String, MapperPreflightResult.MapperStatementRef> expected = new LinkedHashMap<>();
        for (MapperPreflightResult.MapperStatementRef statement : preflight.statements()) {
            expected.put(statement.id(), statement);
        }
        Set<String> seenIds = new HashSet<>();
        if (semantic.statements().size() != expected.size()) {
            reject("Statement coverage does not match the Mapper XML");
        }
        for (MapperStatementSemantic statement : semantic.statements()) {
            validateStatement(statement, trustedSourceFile, expected, seenIds);
        }
        if (seenIds.size() != expected.size() || !seenIds.equals(expected.keySet())) {
            reject("Statement coverage does not match the Mapper XML");
        }
    }

    private static void validateStatement(
            MapperStatementSemantic statement,
            String trustedSourceFile,
            Map<String, MapperPreflightResult.MapperStatementRef> expected,
            Set<String> seenIds) {
        if (statement == null) {
            reject("Statement semantic is missing");
        }
        requireText(statement.id(), "statement id");
        if (statement.operation() == null) {
            reject("Statement operation is missing");
        }
        requireText(statement.description(), "statement description");
        requireList(statement.tables(), "tables");
        requireList(statement.columns(), "columns");
        requireList(statement.relationships(), "relationships");
        requireList(statement.fixedFilters(), "fixedFilters");
        requireList(statement.dynamicFilters(), "dynamicFilters");
        requireList(statement.groupBy(), "groupBy");
        requireList(statement.orderBy(), "orderBy");
        requireList(statement.businessMeanings(), "businessMeanings");
        requireList(statement.evidence(), "evidence");
        if (!seenIds.add(statement.id())) {
            reject("Duplicate statement id");
        }
        MapperPreflightResult.MapperStatementRef expectedStatement = expected.get(statement.id());
        if (expectedStatement == null) {
            reject("Statement id is not present in the Mapper XML");
        }
        if (expectedStatement.operation() != statement.operation()) {
            reject("Statement operation does not match the Mapper XML");
        }
        for (TableRef table : statement.tables()) {
            if (table == null || isBlank(table.table())) {
                reject("Table reference is incomplete");
            }
        }
        for (ColumnRef column : statement.columns()) {
            if (column == null || isBlank(column.column()) || column.usage() == null) {
                reject("Column reference is incomplete");
            }
        }
        for (RelationshipSemantic relationship : statement.relationships()) {
            validateRelationship(relationship);
        }
        for (FilterSemantic filter : statement.fixedFilters()) {
            validateFilter(filter, statement.businessMeanings());
        }
        for (DynamicFilterSemantic filter : statement.dynamicFilters()) {
            validateDynamicFilter(filter);
        }
        for (String groupBy : statement.groupBy()) {
            requireText(groupBy, "groupBy expression");
        }
        for (String orderBy : statement.orderBy()) {
            requireText(orderBy, "orderBy expression");
        }
        for (BusinessMeaning meaning : statement.businessMeanings()) {
            validateBusinessMeaning(meaning);
        }
        if (statement.evidence().isEmpty()) {
            reject("Statement evidence is required");
        }
        for (SemanticEvidence evidence : statement.evidence()) {
            validateEvidence(evidence, statement.id(), trustedSourceFile);
        }
    }

    private static void validateRelationship(RelationshipSemantic relationship) {
        if (relationship == null
                || isBlank(relationship.leftTable())
                || isBlank(relationship.leftColumn())
                || isBlank(relationship.rightTable())
                || isBlank(relationship.rightColumn())
                || relationship.joinType() == null
                || isBlank(relationship.expression())) {
            reject("Relationship is incomplete");
        }
        requireConfidence(relationship.confidence());
    }

    private static void validateFilter(FilterSemantic filter, List<BusinessMeaning> meanings) {
        if (filter == null || isBlank(filter.expression())) {
            reject("Fixed filter is incomplete");
        }
        requireConfidence(filter.confidence());
        if (filter.possibleMeaning() != null && !filter.possibleMeaning().isBlank()) {
            boolean matched = meanings.stream()
                    .anyMatch(meaning -> filter.possibleMeaning().equals(meaning.name())
                            || filter.possibleMeaning().equals(meaning.description()));
            if (!matched) {
                reject("Filter possibleMeaning has no corresponding business meaning");
            }
        }
    }

    private static void validateDynamicFilter(DynamicFilterSemantic filter) {
        if (filter == null
                || isBlank(filter.parameter())
                || isBlank(filter.expression())
                || isBlank(filter.condition())) {
            reject("Dynamic filter is incomplete");
        }
        requireConfidence(filter.confidence());
    }

    private static void validateBusinessMeaning(BusinessMeaning meaning) {
        if (meaning == null
                || isBlank(meaning.name())
                || isBlank(meaning.description())
                || isBlank(meaning.derivedFrom())) {
            reject("Business meaning is incomplete");
        }
        requireConfidence(meaning.confidence());
        if (!meaning.meetsConfidenceThreshold()) {
            reject("Business meaning confidence is below the allowed threshold");
        }
    }

    private static void validateEvidence(SemanticEvidence evidence, String statementId, String trustedSourceFile) {
        if (evidence == null
                || isBlank(evidence.sourceFile())
                || isBlank(evidence.statementId())
                || evidence.evidenceType() == null
                || isBlank(evidence.evidence())) {
            reject("Statement evidence is incomplete");
        }
        if (!trustedSourceFile.equals(evidence.sourceFile())) {
            reject("Evidence source file does not match the processed Mapper file");
        }
        if (!statementId.equals(evidence.statementId())) {
            reject("Evidence statement id does not belong to the statement");
        }
    }

    private static void requireList(List<?> value, String name) {
        if (value == null) {
            reject(name + " must not be null");
        }
    }

    private static void requireText(String value, String name) {
        if (isBlank(value)) {
            reject(name + " must not be blank");
        }
    }

    private static void requireConfidence(double confidence) {
        if (confidence < 0.0d || confidence > 1.0d || Double.isNaN(confidence)) {
            reject("Confidence must be between 0.0 and 1.0");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void reject(String message) {
        throw new MapperSemanticExtractionException(MapperSemanticFailureType.VALIDATION_ERROR, message);
    }

    public String trustedMapperName(String relativePath) {
        return SourcePaths.mapperName(relativePath);
    }
}
