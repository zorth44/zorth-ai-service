package com.zorth.aiplatform.semantic.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zorth.aiplatform.semantic.exception.MapperSemanticExtractionException;
import com.zorth.aiplatform.semantic.model.SqlOperation;
import com.zorth.aiplatform.semantic.report.MapperSemanticFailureType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class MapperXmlPreflightTest {

    private final MapperXmlPreflight preflight = new MapperXmlPreflight();

    @Test
    void extractsNamespaceAndInventory() throws Exception {
        MapperPreflightResult result = preflight.inspect(read("mappers/ordinary-select/OrdinarySelectMapper.xml"));
        assertEquals("com.example.order.mapper.OrdinarySelectMapper", result.namespace());
        assertEquals(List.of(new MapperPreflightResult.MapperStatementRef("findOne", SqlOperation.SELECT)), result.statements());
    }

    @Test
    void extractsMixedOperations() throws Exception {
        MapperPreflightResult result = preflight.inspect(read("mappers/mixed-ops/MixedOperationsMapper.xml"));
        assertEquals(4, result.statements().size());
        assertEquals(SqlOperation.SELECT, result.statements().get(0).operation());
        assertEquals(SqlOperation.INSERT, result.statements().get(1).operation());
        assertEquals(SqlOperation.UPDATE, result.statements().get(2).operation());
        assertEquals(SqlOperation.DELETE, result.statements().get(3).operation());
    }

    @Test
    void rejectsDuplicateStatementIds() {
        String xml = """
                <mapper namespace="ns">
                  <select id="findOne">SELECT 1</select>
                  <select id="findOne">SELECT 2</select>
                </mapper>
                """;
        MapperSemanticExtractionException ex =
                assertThrows(MapperSemanticExtractionException.class, () -> preflight.inspect(xml));
        assertEquals(MapperSemanticFailureType.XML_VALIDATION_ERROR, ex.failureType());
    }

    @Test
    void rejectsBlankStatementIds() {
        String xml = """
                <mapper namespace="ns">
                  <select id=" ">SELECT 1</select>
                </mapper>
                """;
        MapperSemanticExtractionException ex =
                assertThrows(MapperSemanticExtractionException.class, () -> preflight.inspect(xml));
        assertEquals(MapperSemanticFailureType.XML_VALIDATION_ERROR, ex.failureType());
    }

    @Test
    void classifiesNonMapperXml() throws Exception {
        MapperSemanticExtractionException ex = assertThrows(
                MapperSemanticExtractionException.class,
                () -> preflight.inspect(read("mappers/non-mapper/not-a-mapper.xml")));
        assertEquals(MapperSemanticFailureType.XML_VALIDATION_ERROR, ex.failureType());
    }

    @Test
    void classifiesMalformedXml() throws Exception {
        MapperSemanticExtractionException ex = assertThrows(
                MapperSemanticExtractionException.class,
                () -> preflight.inspect(read("mappers/malformed/BrokenMapper.xml")));
        assertEquals(MapperSemanticFailureType.XML_VALIDATION_ERROR, ex.failureType());
    }

    @Test
    void inspectsMybatisDoctypeWithoutExternalIo() throws Exception {
        MapperPreflightResult result = preflight.inspect(read("mappers/doctype-cdata/DoctypeCdataMapper.xml"));
        assertEquals("com.example.order.mapper.DoctypeCdataMapper", result.namespace());
        assertEquals("findByAmount", result.statements().get(0).id());
    }

    @Test
    void preservesCdataWithoutParsingSql() throws Exception {
        MapperPreflightResult result = preflight.inspect(read("mappers/dynamic-if/DynamicIfMapper.xml"));
        assertEquals("queryOrders", result.statements().get(0).id());
        assertEquals(SqlOperation.SELECT, result.statements().get(0).operation());
    }

    @Test
    void doesNotExpandExternalEntities() throws Exception {
        Path secret = Files.createTempFile("semantic-secret", ".txt");
        Files.writeString(secret, "TOP-SECRET-VALUE");
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE mapper [
                  <!ENTITY xxe SYSTEM "%s">
                ]>
                <mapper namespace="ns">
                  <select id="findSecret">SELECT '&xxe;'</select>
                </mapper>
                """.formatted(secret.toUri());
        try {
            MapperPreflightResult result = preflight.inspect(xml);
            assertTrue(result.namespace().equals("ns"));
            assertTrue(result.statements().stream().noneMatch(statement -> statement.id().contains("TOP-SECRET")));
        }
        catch (MapperSemanticExtractionException ex) {
            assertEquals(MapperSemanticFailureType.XML_VALIDATION_ERROR, ex.failureType());
        }
        finally {
            Files.deleteIfExists(secret);
        }
    }

    private static String read(String classpath) throws Exception {
        return new String(new ClassPathResource(classpath).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
