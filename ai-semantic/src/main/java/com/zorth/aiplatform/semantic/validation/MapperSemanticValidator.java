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
import com.zorth.aiplatform.semantic.model.TableKind;
import com.zorth.aiplatform.semantic.report.MapperSemanticFailureType;
import com.zorth.aiplatform.semantic.scan.MapperPreflightResult;
import com.zorth.aiplatform.semantic.scan.SourcePaths;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
            validateTable(table);
        }
        for (ColumnRef column : statement.columns()) {
            if (column == null || isBlank(column.column()) || column.usage() == null) {
                reject("Column reference is incomplete");
            }
            requireOptionalText(column.table(), "column table");
            requireOptionalText(column.alias(), "column alias");
        }
        for (RelationshipSemantic relationship : statement.relationships()) {
            validateRelationship(relationship);
        }
        for (FilterSemantic filter : statement.fixedFilters()) {
            validateFilter(filter);
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
        Set<String> meaningKeys = new HashSet<>();
        for (BusinessMeaning meaning : statement.businessMeanings()) {
            validateBusinessMeaning(meaning, statement, meaningKeys);
        }
        if (statement.evidence().isEmpty()) {
            reject("Statement evidence is required");
        }
        for (SemanticEvidence evidence : statement.evidence()) {
            validateEvidence(evidence, statement.id(), trustedSourceFile);
        }
    }

    private static void validateTable(TableRef table) {
        if (table == null || table.kind() == null) {
            reject("Table reference is incomplete");
        }
        requireOptionalText(table.table(), "table name");
        requireOptionalText(table.alias(), "table alias");
        switch (table.kind()) {
            case PHYSICAL, CTE -> {
                if (isBlank(table.table())) {
                    reject("Physical and CTE relations require a visible table name");
                }
            }
            case DERIVED -> {
                if (table.table() != null || isBlank(table.alias())) {
                    reject("Derived relations require a null table and visible alias");
                }
            }
            case UNKNOWN -> {
                if (table.table() == null && table.alias() == null) {
                    reject("Unknown relations must preserve a visible source token");
                }
            }
        }
    }

    private static void validateRelationship(RelationshipSemantic relationship) {
        if (relationship == null
                || isBlank(relationship.leftColumn())
                || isBlank(relationship.rightColumn())
                || relationship.joinType() == null
                || isBlank(relationship.expression())) {
            reject("Relationship is incomplete");
        }
        requireOptionalText(relationship.leftTable(), "relationship left table");
        requireOptionalText(relationship.rightTable(), "relationship right table");
        requireConfidence(relationship.confidence());
    }

    private static void validateFilter(FilterSemantic filter) {
        if (filter == null || isBlank(filter.expression())) {
            reject("Fixed filter is incomplete");
        }
        requireOptionalText(filter.table(), "fixed filter table");
        requireOptionalText(filter.column(), "fixed filter column");
        requireOptionalText(filter.operator(), "fixed filter operator");
        requireOptionalText(filter.value(), "fixed filter value");
        requireConfidence(filter.confidence());
    }

    private static void validateDynamicFilter(DynamicFilterSemantic filter) {
        if (filter == null
                || isBlank(filter.parameter())
                || isBlank(filter.expression())
                || isBlank(filter.condition())) {
            reject("Dynamic filter is incomplete");
        }
        requireOptionalText(filter.table(), "dynamic filter table");
        requireOptionalText(filter.column(), "dynamic filter column");
        requireOptionalText(filter.operator(), "dynamic filter operator");
        if (containsDynamicXml(filter.expression()) || containsDynamicXml(filter.condition())) {
            reject("Dynamic filter fields must not contain enclosing XML");
        }
        if (looksLikeSqlFragment(filter.condition())
                || (looksLikeOgnlGuard(filter.expression()) && looksLikeSqlFragment(filter.condition()))) {
            reject("Dynamic filter expression and condition do not match the SQL/OGNL contract");
        }
        requireConfidence(filter.confidence());
    }

    private static void validateBusinessMeaning(
            BusinessMeaning meaning, MapperStatementSemantic statement, Set<String> meaningKeys) {
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
        String key = normalize(meaning.name()) + "\n" + normalize(meaning.description());
        if (!meaningKeys.add(key)) {
            reject("Duplicate business meaning");
        }
        if (isGenericCrudParaphrase(meaning, statement)) {
            reject("Business meaning is only a generic CRUD paraphrase");
        }
        List<SemanticEvidence> inferenceEvidence = statement.evidence().stream()
                .filter(Objects::nonNull)
                .filter(evidence -> evidence.evidenceType() == com.zorth.aiplatform.semantic.model.EvidenceType.INFERENCE)
                .toList();
        if (inferenceEvidence.isEmpty()) {
            reject("Business meaning requires INFERENCE evidence");
        }
        if (meaning.confidence() >= 0.9d && !hasStrongCodeEvidence(meaning, inferenceEvidence)) {
            reject("High-confidence business meaning requires explicit strong code evidence");
        }
    }

    private static boolean containsDynamicXml(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.matches("(?s).*<(?:/?)(?:if|when|otherwise|foreach|choose|where|trim|set)\\b.*");
    }

    private static boolean looksLikeSqlFragment(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("#{")
                || normalized.contains("${")
                || normalized.matches("(?s).*(?:\\bselect\\b|\\bfrom\\b|\\bjoin\\b|\\bwhere\\b|\\border\\s+by\\b|\\bgroup\\s+by\\b|\\bin\\s*\\().*");
    }

    private static boolean looksLikeOgnlGuard(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("!= null")
                || normalized.contains("== null")
                || normalized.contains(".size")
                || normalized.contains(".isempty")
                || normalized.contains(" and ")
                || normalized.contains(" or ");
    }

    private static boolean isGenericCrudParaphrase(
            BusinessMeaning meaning, MapperStatementSemantic statement) {
        String name = normalize(meaning.name());
        String description = normalize(meaning.description());
        if (name.equals(normalize(statement.id())) || description.equals(normalize(statement.description()))) {
            return true;
        }
        String generic = "(?:create|insert|save|update|modify|delete|remove|select|query|fetch|get)"
                + "(?:item|items|record|records|row|rows|data|entity|entities|object|objects|statement)";
        return name.matches(generic) || description.matches(generic);
    }

    private static boolean hasStrongCodeEvidence(
            BusinessMeaning meaning, List<SemanticEvidence> inferenceEvidence) {
        StringBuilder text = new StringBuilder(meaning.derivedFrom().toLowerCase(Locale.ROOT));
        for (SemanticEvidence evidence : inferenceEvidence) {
            text.append(' ').append(evidence.evidence().toLowerCase(Locale.ROOT));
        }
        String combined = text.toString();
        boolean literalComment = combined.contains("<!--") && combined.contains("-->");
        boolean namedSqlFragment = combined.matches("(?s).*<sql\\s+[^>]*id\\s*=.*");
        return literalComment || namedSqlFragment;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
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

    private static void requireOptionalText(String value, String name) {
        if (value != null && value.isBlank()) {
            reject(name + " must be null or non-blank");
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
