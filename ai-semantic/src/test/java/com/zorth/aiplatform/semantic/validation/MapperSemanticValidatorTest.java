package com.zorth.aiplatform.semantic.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zorth.aiplatform.semantic.exception.MapperSemanticExtractionException;
import com.zorth.aiplatform.semantic.model.BusinessMeaning;
import com.zorth.aiplatform.semantic.model.ColumnRef;
import com.zorth.aiplatform.semantic.model.ColumnUsage;
import com.zorth.aiplatform.semantic.model.DynamicFilterSemantic;
import com.zorth.aiplatform.semantic.model.EvidenceType;
import com.zorth.aiplatform.semantic.model.FilterSemantic;
import com.zorth.aiplatform.semantic.model.MapperSemantic;
import com.zorth.aiplatform.semantic.model.MapperStatementSemantic;
import com.zorth.aiplatform.semantic.model.SemanticEvidence;
import com.zorth.aiplatform.semantic.model.SqlOperation;
import com.zorth.aiplatform.semantic.model.TableKind;
import com.zorth.aiplatform.semantic.model.TableRef;
import com.zorth.aiplatform.semantic.report.MapperSemanticFailureType;
import com.zorth.aiplatform.semantic.scan.MapperPreflightResult;
import com.zorth.aiplatform.semantic.support.MapperSemanticFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

class MapperSemanticValidatorTest {

    private final MapperSemanticValidator validator = new MapperSemanticValidator();

    @Test
    void acceptsTrustedCompleteArtifact() {
        MapperSemantic semantic = MapperSemanticFixtures.singleSelect(
                "mapper/order/OrderMapper.xml", "hash", "OrderMapper", "ns", "findOne");
        assertDoesNotThrow(() -> validator.validate(
                semantic, "hash", "mapper/order/OrderMapper.xml", "OrderMapper", MapperSemanticFixtures.preflight("ns", "findOne", SqlOperation.SELECT)));
    }

    @Test
    void rejectsBlankAndNullCollections() {
        MapperSemantic blank = new MapperSemantic(
                " ", "hash", "OrderMapper", "ns", "mapper/order/OrderMapper.xml", "summary", List.of());
        assertValidation(blank, "hash", "mapper/order/OrderMapper.xml", "OrderMapper", MapperSemanticFixtures.preflight("ns", "findOne", SqlOperation.SELECT));

        MapperStatementSemantic statement = MapperSemanticFixtures.selectStatement("mapper/order/OrderMapper.xml", "findOne");
        MapperStatementSemantic nullRelationships = new MapperStatementSemantic(
                statement.id(),
                statement.operation(),
                statement.description(),
                statement.tables(),
                statement.columns(),
                null,
                statement.fixedFilters(),
                statement.dynamicFilters(),
                statement.groupBy(),
                statement.orderBy(),
                statement.businessMeanings(),
                statement.evidence());
        MapperSemantic nullList = new MapperSemantic(
                MapperSemantic.SCHEMA_VERSION,
                "hash",
                "OrderMapper",
                "ns",
                "mapper/order/OrderMapper.xml",
                "summary",
                List.of(nullRelationships));
        assertValidation(
                nullList,
                "hash",
                "mapper/order/OrderMapper.xml",
                "OrderMapper",
                MapperSemanticFixtures.preflight("ns", "findOne", SqlOperation.SELECT));
    }

    @Test
    void rejectsMissingDuplicateAndExtraStatements() {
        MapperSemantic missing = MapperSemanticFixtures.singleSelect(
                "file.xml", "hash", "File", "ns", "findOne");
        MapperPreflightResult two = new MapperPreflightResult(
                "ns",
                List.of(
                        new MapperPreflightResult.MapperStatementRef("findOne", SqlOperation.SELECT),
                        new MapperPreflightResult.MapperStatementRef("findAll", SqlOperation.SELECT)));
        assertValidation(missing, "hash", "file.xml", "File", two);

        MapperSemantic extra = new MapperSemantic(
                MapperSemantic.SCHEMA_VERSION,
                "hash",
                "File",
                "ns",
                "file.xml",
                "summary",
                List.of(
                        MapperSemanticFixtures.selectStatement("file.xml", "findOne"),
                        MapperSemanticFixtures.selectStatement("file.xml", "hallucinated")));
        assertValidation(extra, "hash", "file.xml", "File", MapperSemanticFixtures.preflight("ns", "findOne", SqlOperation.SELECT));

        MapperSemantic duplicate = new MapperSemantic(
                MapperSemantic.SCHEMA_VERSION,
                "hash",
                "File",
                "ns",
                "file.xml",
                "summary",
                List.of(
                        MapperSemanticFixtures.selectStatement("file.xml", "findOne"),
                        MapperSemanticFixtures.selectStatement("file.xml", "findOne")));
        assertValidation(duplicate, "hash", "file.xml", "File", MapperSemanticFixtures.preflight("ns", "findOne", SqlOperation.SELECT));
    }

    @Test
    void rejectsWrongOperationAndForeignEvidence() {
        MapperSemantic wrongOp = new MapperSemantic(
                MapperSemantic.SCHEMA_VERSION,
                "hash",
                "File",
                "ns",
                "file.xml",
                "summary",
                List.of(MapperSemanticFixtures.statement("file.xml", "updateOne", SqlOperation.SELECT)));
        assertValidation(
                wrongOp,
                "hash",
                "file.xml",
                "File",
                MapperSemanticFixtures.preflight("ns", "updateOne", SqlOperation.UPDATE));

        MapperStatementSemantic foreignEvidence = new MapperStatementSemantic(
                "findOne",
                SqlOperation.SELECT,
                "desc",
                List.of(new TableRef("t_order", "o", TableKind.PHYSICAL)),
                List.of(new ColumnRef("t_order", "id", null, ColumnUsage.SELECT)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new SemanticEvidence("file.xml", "otherId", EvidenceType.SQL, "SELECT 1")));
        MapperSemantic semantic = new MapperSemantic(
                MapperSemantic.SCHEMA_VERSION,
                "hash",
                "File",
                "ns",
                "file.xml",
                "summary",
                List.of(foreignEvidence));
        assertValidation(semantic, "hash", "file.xml", "File", MapperSemanticFixtures.preflight("ns", "findOne", SqlOperation.SELECT));
    }

    @Test
    void rejectsInvalidConfidenceAndWeakInference() {
        MapperStatementSemantic weak = replaceMeanings(
                MapperSemanticFixtures.selectStatement("file.xml", "findOne"),
                List.of(new BusinessMeaning("guess", "guess", "none", 0.6d)));
        assertValidation(
                wrap(weak),
                "hash",
                "file.xml",
                "File",
                MapperSemanticFixtures.preflight("ns", "findOne", SqlOperation.SELECT));

        MapperStatementSemantic invalidConfidence = replaceFilters(
                MapperSemanticFixtures.selectStatement("file.xml", "findOne"),
                List.of(new FilterSemantic("1=1", null, null, "=", "1", 1.1d)));
        assertValidation(
                wrap(invalidConfidence),
                "hash",
                "file.xml",
                "File",
                MapperSemanticFixtures.preflight("ns", "findOne", SqlOperation.SELECT));
    }

    @Test
    void acceptsNullableFieldsAndDerivedRelations() {
        MapperStatementSemantic base = MapperSemanticFixtures.selectStatement("file.xml", "findOne");
        MapperStatementSemantic derived = replaceTables(
                base,
                List.of(
                        new TableRef(null, "bn", TableKind.DERIVED),
                        new TableRef("tasks", "t", TableKind.PHYSICAL)));
        assertDoesNotThrow(() -> validator.validate(
                wrap(derived),
                "hash",
                "file.xml",
                "File",
                MapperSemanticFixtures.preflight("ns", "findOne", SqlOperation.SELECT)));
    }

    @Test
    void rejectsEmptyOptionalScalarsAndInvalidRelationShapes() {
        MapperStatementSemantic emptyAlias = replaceTables(
                MapperSemanticFixtures.selectStatement("file.xml", "findOne"),
                List.of(new TableRef("t_order", "", TableKind.PHYSICAL)));
        assertValidation(
                wrap(emptyAlias), "hash", "file.xml", "File",
                MapperSemanticFixtures.preflight("ns", "findOne", SqlOperation.SELECT));

        MapperStatementSemantic fabricatedDerived = replaceTables(
                MapperSemanticFixtures.selectStatement("file.xml", "findOne"),
                List.of(new TableRef("derived_union", "bn", TableKind.DERIVED)));
        assertValidation(
                wrap(fabricatedDerived), "hash", "file.xml", "File",
                MapperSemanticFixtures.preflight("ns", "findOne", SqlOperation.SELECT));

        MapperStatementSemantic missingUnknownToken = replaceTables(
                MapperSemanticFixtures.selectStatement("file.xml", "findOne"),
                List.of(new TableRef(null, null, TableKind.UNKNOWN)));
        assertValidation(
                wrap(missingUnknownToken), "hash", "file.xml", "File",
                MapperSemanticFixtures.preflight("ns", "findOne", SqlOperation.SELECT));
    }

    @Test
    void rejectsDynamicXmlAndReversedDynamicFields() {
        MapperStatementSemantic xmlExpression = replaceDynamicFilters(
                MapperSemanticFixtures.selectStatement("file.xml", "findOne"),
                List.of(new DynamicFilterSemantic(
                        "startTime",
                        "<if test=\"startTime != null\">o.created_at >= #{startTime}</if>",
                        "t_order",
                        "created_at",
                        ">=",
                        "startTime != null",
                        1.0d)));
        assertValidation(
                wrap(xmlExpression), "hash", "file.xml", "File",
                MapperSemanticFixtures.preflight("ns", "findOne", SqlOperation.SELECT));

        MapperStatementSemantic reversed = replaceDynamicFilters(
                MapperSemanticFixtures.selectStatement("file.xml", "findOne"),
                List.of(new DynamicFilterSemantic(
                        "startTime",
                        "startTime != null",
                        "t_order",
                        "created_at",
                        ">=",
                        "o.created_at >= #{startTime}",
                        1.0d)));
        assertValidation(
                wrap(reversed), "hash", "file.xml", "File",
                MapperSemanticFixtures.preflight("ns", "findOne", SqlOperation.SELECT));
    }

    @Test
    void rejectsGenericAndUnsupportedHighConfidenceBusinessMeanings() {
        MapperStatementSemantic generic = withMeaningAndInferenceEvidence(
                new BusinessMeaning("Create item", "Create item", "statement id=createItem", 0.8d));
        assertValidation(
                wrap(generic), "hash", "file.xml", "File",
                MapperSemanticFixtures.preflight("ns", "findOne", SqlOperation.SELECT));

        MapperStatementSemantic unsupportedHighConfidence = withMeaningAndInferenceEvidence(
                new BusinessMeaning(
                        "Completed orders", "Find completed orders", "statement id and status predicate", 0.95d));
        assertValidation(
                wrap(unsupportedHighConfidence), "hash", "file.xml", "File",
                MapperSemanticFixtures.preflight("ns", "findOne", SqlOperation.SELECT));
    }

    @Test
    void acceptsEvidenceBackedBusinessMeaning() {
        MapperStatementSemantic supported = withMeaningAndInferenceEvidence(
                new BusinessMeaning(
                        "Overdue orders",
                        "Orders explicitly described as overdue",
                        "<!-- business rule: overdue orders -->",
                        0.95d));
        assertDoesNotThrow(() -> validator.validate(
                wrap(supported),
                "hash",
                "file.xml",
                "File",
                MapperSemanticFixtures.preflight("ns", "findOne", SqlOperation.SELECT)));
    }

    @Test
    void trustedProvenanceOverwritesModelEchoedPathAndHash() {
        MapperSemantic echoed = MapperSemanticFixtures.singleSelect(
                "wrong/path.xml", "wrong-hash", "OrderMapper", "ns", "findOne");
        MapperSemantic trusted = echoed.withTrustedProvenance("real-hash", "mapper/order/OrderMapper.xml");
        assertEquals("real-hash", trusted.sourceHash());
        assertEquals("mapper/order/OrderMapper.xml", trusted.sourceFile());
        assertEquals("mapper/order/OrderMapper.xml", trusted.statements().get(0).evidence().get(0).sourceFile());
        assertDoesNotThrow(() -> validator.validate(
                trusted,
                "real-hash",
                "mapper/order/OrderMapper.xml",
                "OrderMapper",
                MapperSemanticFixtures.preflight("ns", "findOne", SqlOperation.SELECT)));
        assertValidation(
                echoed,
                "real-hash",
                "mapper/order/OrderMapper.xml",
                "OrderMapper",
                MapperSemanticFixtures.preflight("ns", "findOne", SqlOperation.SELECT));
    }

    private static MapperSemantic wrap(MapperStatementSemantic statement) {
        return new MapperSemantic(
                MapperSemantic.SCHEMA_VERSION, "hash", "File", "ns", "file.xml", "summary", List.of(statement));
    }

    private static MapperStatementSemantic replaceMeanings(
            MapperStatementSemantic statement, List<BusinessMeaning> meanings) {
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
                meanings,
                statement.evidence());
    }

    private static MapperStatementSemantic replaceFilters(
            MapperStatementSemantic statement, List<FilterSemantic> filters) {
        return new MapperStatementSemantic(
                statement.id(),
                statement.operation(),
                statement.description(),
                statement.tables(),
                statement.columns(),
                statement.relationships(),
                filters,
                statement.dynamicFilters(),
                statement.groupBy(),
                statement.orderBy(),
                statement.businessMeanings(),
                statement.evidence());
    }

    private static MapperStatementSemantic replaceTables(
            MapperStatementSemantic statement, List<TableRef> tables) {
        return new MapperStatementSemantic(
                statement.id(), statement.operation(), statement.description(), tables, statement.columns(),
                statement.relationships(), statement.fixedFilters(), statement.dynamicFilters(), statement.groupBy(),
                statement.orderBy(), statement.businessMeanings(), statement.evidence());
    }

    private static MapperStatementSemantic replaceDynamicFilters(
            MapperStatementSemantic statement, List<DynamicFilterSemantic> dynamicFilters) {
        return new MapperStatementSemantic(
                statement.id(), statement.operation(), statement.description(), statement.tables(), statement.columns(),
                statement.relationships(), statement.fixedFilters(), dynamicFilters, statement.groupBy(),
                statement.orderBy(), statement.businessMeanings(), statement.evidence());
    }

    private static MapperStatementSemantic withMeaningAndInferenceEvidence(BusinessMeaning meaning) {
        MapperStatementSemantic statement = MapperSemanticFixtures.selectStatement("file.xml", "findOne");
        List<SemanticEvidence> evidence = List.of(
                statement.evidence().get(0),
                new SemanticEvidence("file.xml", "findOne", EvidenceType.INFERENCE, meaning.derivedFrom()));
        return new MapperStatementSemantic(
                statement.id(), statement.operation(), statement.description(), statement.tables(), statement.columns(),
                statement.relationships(), statement.fixedFilters(), statement.dynamicFilters(), statement.groupBy(),
                statement.orderBy(), List.of(meaning), evidence);
    }

    private void assertValidation(
            MapperSemantic semantic,
            String hash,
            String sourceFile,
            String mapperName,
            MapperPreflightResult preflight) {
        MapperSemanticExtractionException ex = assertThrows(
                MapperSemanticExtractionException.class,
                () -> validator.validate(semantic, hash, sourceFile, mapperName, preflight));
        assertEquals(MapperSemanticFailureType.VALIDATION_ERROR, ex.failureType());
    }
}
