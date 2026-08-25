package com.zorth.aiplatform.semantic.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zorth.aiplatform.semantic.generation.MapperSemanticJsonPublisher;
import com.zorth.aiplatform.semantic.support.MapperSemanticFixtures;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MapperSemanticArtifactTest {

    private final ObjectMapper objectMapper = MapperSemanticJsonPublisher.defaultObjectMapper();

    @Test
    void constructsRequiredProvenanceAndCollections() {
        MapperSemantic semantic = MapperSemanticFixtures.singleSelect(
                "mapper/order/OrderMapper.xml",
                "abc",
                "OrderMapper",
                "com.example.order.mapper.OrderMapper",
                "findOne");

        assertEquals(MapperSemantic.SCHEMA_VERSION, semantic.schemaVersion());
        assertEquals("abc", semantic.sourceHash());
        assertEquals("mapper/order/OrderMapper.xml", semantic.sourceFile());
        assertEquals(1, semantic.statements().size());
        assertTrue(semantic.statements().get(0).relationships().isEmpty());
        assertTrue(semantic.statements().get(0).dynamicFilters().isEmpty());
        assertTrue(semantic.statements().get(0).groupBy().isEmpty());
        assertTrue(semantic.statements().get(0).orderBy().isEmpty());
        assertTrue(semantic.statements().get(0).businessMeanings().isEmpty());
    }

    @Test
    void serializesEnumsAsClosedVocabularyNames() throws Exception {
        JsonNode node = objectMapper.valueToTree(MapperSemanticFixtures.leftJoin());
        assertEquals("LEFT_JOIN", node.get("joinType").asText());
        assertEquals("SELECT", objectMapper.valueToTree(ColumnUsage.SELECT).asText());
        assertEquals("UNKNOWN", objectMapper.valueToTree(SqlOperation.UNKNOWN).asText());
        assertEquals("UNRESOLVED_INCLUDE", objectMapper.valueToTree(EvidenceType.UNRESOLVED_INCLUDE).asText());
    }

    @Test
    void serializesEmptyCollectionsAsArrays() throws Exception {
        MapperSemantic semantic = MapperSemanticFixtures.singleSelect(
                "mapper/order/OrderMapper.xml", "abc", "OrderMapper", "ns", "findOne");
        JsonNode statement = objectMapper.valueToTree(semantic).get("statements").get(0);
        assertTrue(statement.get("relationships").isArray());
        assertEquals(0, statement.get("relationships").size());
        assertTrue(statement.get("dynamicFilters").isArray());
        assertEquals(0, statement.get("businessMeanings").size());
        assertFalse(statement.get("relationships").isNull());
    }

    @Test
    void confidenceBoundariesAreRepresentable() {
        FilterSemantic min = new FilterSemantic("1=1", null, null, "=", "1", null, 0.0d);
        FilterSemantic max = new FilterSemantic("1=1", null, null, "=", "1", null, 1.0d);
        assertEquals(0.0d, min.confidence());
        assertEquals(1.0d, max.confidence());
    }

    @Test
    void businessMeaningThresholdRejectsWeakInference() {
        BusinessMeaning weak = new BusinessMeaning("guess", "guess", "none", 0.69d);
        BusinessMeaning accepted = MapperSemanticFixtures.completedOrders();
        assertFalse(weak.meetsConfidenceThreshold());
        assertTrue(accepted.meetsConfidenceThreshold());
        assertEquals(0.7d, BusinessMeaning.MIN_CONFIDENCE);
    }

    @Test
    void artifactTypesDoNotExposeMapsOrProviderTypes() {
        List<Class<?>> types = List.of(
                MapperSemantic.class,
                MapperStatementSemantic.class,
                TableRef.class,
                ColumnRef.class,
                RelationshipSemantic.class,
                FilterSemantic.class,
                DynamicFilterSemantic.class,
                BusinessMeaning.class,
                SemanticEvidence.class);
        for (Class<?> type : types) {
            for (RecordComponent component : type.getRecordComponents()) {
                assertFalse(Map.class.isAssignableFrom(component.getType()), type.getSimpleName());
                assertFalse(component.getType().getName().contains("springframework.ai"), type.getSimpleName());
                assertFalse(component.getType().getName().contains("ChatResponse"), type.getSimpleName());
            }
        }
        assertFalse(Arrays.stream(MapperSemantic.class.getRecordComponents())
                .anyMatch(component -> "modelName".equals(component.getName())
                        || "provider".equals(component.getName())
                        || "createdAt".equals(component.getName())));
    }

    @Test
    void jacksonRoundTripPreservesEquality() throws Exception {
        MapperSemantic original = new MapperSemantic(
                MapperSemantic.SCHEMA_VERSION,
                "abc",
                "LeftJoinMapper",
                "com.example.order.mapper.LeftJoinMapper",
                "left-join/LeftJoinMapper.xml",
                "Orders with users",
                List.of(new MapperStatementSemantic(
                        "queryUserOrders",
                        SqlOperation.SELECT,
                        "Query user orders",
                        List.of(new TableRef("t_order", "o"), new TableRef("t_user", "u")),
                        List.of(new ColumnRef("t_order", "amount", null, ColumnUsage.SELECT)),
                        List.of(MapperSemanticFixtures.leftJoin()),
                        List.of(new FilterSemantic(
                                "o.status = '03'", "t_order", "status", "=", "03", null, 1.0d)),
                        List.of(MapperSemanticFixtures.startTimeFilter()),
                        List.of(),
                        List.of("o.create_time DESC"),
                        List.of(),
                        List.of(new SemanticEvidence(
                                "left-join/LeftJoinMapper.xml",
                                "queryUserOrders",
                                EvidenceType.SQL,
                                "LEFT JOIN t_user u ON o.user_id = u.id")))));

        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(original);
        MapperSemantic copy = objectMapper.readValue(json, MapperSemantic.class);
        assertEquals(original, copy);
        assertNull(copy.statements().get(0).columns().get(0).alias());
    }

    @Test
    void unknownColumnOwnershipStaysNull() {
        ColumnRef column = new ColumnRef(null, "amount", null, ColumnUsage.UNKNOWN);
        assertNull(column.table());
        assertEquals(ColumnUsage.UNKNOWN, column.usage());
    }
}
