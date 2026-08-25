package com.zorth.aiplatform.semantic.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zorth.aiplatform.semantic.exception.MapperSemanticExtractionException;
import com.zorth.aiplatform.semantic.model.MapperSemantic;
import com.zorth.aiplatform.semantic.report.MapperSemanticFailureType;
import com.zorth.aiplatform.semantic.support.MapperSemanticFixtures;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MapperSemanticJsonPublisherTest {

    @TempDir
    Path tempDir;

    @Test
    void writesFirstFileAndSuccessfulOverwrite() throws Exception {
        Path target = tempDir.resolve("module-a/mapper/OrderMapper.semantic.json");
        MapperSemantic first = MapperSemanticFixtures.singleSelect(
                "module-a/mapper/OrderMapper.xml", "hash-1", "OrderMapper", "ns", "findOne");
        MapperSemanticJsonPublisher publisher =
                new MapperSemanticJsonPublisher(MapperSemanticJsonPublisher.defaultObjectMapper());
        publisher.publish(target, first);
        assertEquals(first, MapperSemanticJsonPublisher.defaultObjectMapper().readValue(target.toFile(), MapperSemantic.class));

        MapperSemantic second = MapperSemanticFixtures.singleSelect(
                "module-a/mapper/OrderMapper.xml", "hash-2", "OrderMapper", "ns", "findOne");
        publisher.publish(target, second);
        assertEquals(second, MapperSemanticJsonPublisher.defaultObjectMapper().readValue(target.toFile(), MapperSemantic.class));
        assertTrue(listTempFiles().isEmpty());
    }

    @Test
    void serializationFailurePreservesPreviousTargetAndCleansTemp() throws Exception {
        Path target = tempDir.resolve("OrderMapper.semantic.json");
        MapperSemantic previous = MapperSemanticFixtures.singleSelect(
                "OrderMapper.xml", "hash-1", "OrderMapper", "ns", "findOne");
        ObjectMapper objectMapper = MapperSemanticJsonPublisher.defaultObjectMapper();
        new MapperSemanticJsonPublisher(objectMapper).publish(target, previous);

        MapperSemanticJsonPublisher publisher = new MapperSemanticJsonPublisher(
                objectMapper,
                semantic -> {
                    throw new JsonProcessingException("boom") {};
                },
                MapperSemanticJsonPublisher::replaceTarget);
        MapperSemanticExtractionException ex = assertThrows(
                MapperSemanticExtractionException.class,
                () -> publisher.publish(
                        target,
                        MapperSemanticFixtures.singleSelect("OrderMapper.xml", "hash-2", "OrderMapper", "ns", "findOne")));
        assertEquals(MapperSemanticFailureType.WRITE_ERROR, ex.failureType());
        assertEquals(previous, objectMapper.readValue(target.toFile(), MapperSemantic.class));
        assertTrue(listTempFiles().isEmpty());
    }

    @Test
    void readBackFailurePreservesPreviousTarget() throws Exception {
        Path target = tempDir.resolve("OrderMapper.semantic.json");
        MapperSemantic previous = MapperSemanticFixtures.singleSelect(
                "OrderMapper.xml", "hash-1", "OrderMapper", "ns", "findOne");
        ObjectMapper objectMapper = MapperSemanticJsonPublisher.defaultObjectMapper();
        new MapperSemanticJsonPublisher(objectMapper).publish(target, previous);

        ObjectMapper failingRead = new ObjectMapper() {
            @Override
            public <T> T readValue(java.io.File src, Class<T> valueType) throws java.io.IOException {
                throw new JsonProcessingException("bad read") {};
            }
        };
        MapperSemanticJsonPublisher publisher = new MapperSemanticJsonPublisher(failingRead);
        MapperSemanticExtractionException ex = assertThrows(
                MapperSemanticExtractionException.class,
                () -> publisher.publish(
                        target,
                        MapperSemanticFixtures.singleSelect("OrderMapper.xml", "hash-2", "OrderMapper", "ns", "findOne")));
        assertEquals(MapperSemanticFailureType.WRITE_ERROR, ex.failureType());
        assertEquals(previous, objectMapper.readValue(target.toFile(), MapperSemantic.class));
        assertTrue(listTempFiles().isEmpty());
    }

    @Test
    void moveFailurePreservesPreviousTargetAndCleansTemp() throws Exception {
        Path target = tempDir.resolve("OrderMapper.semantic.json");
        MapperSemantic previous = MapperSemanticFixtures.singleSelect(
                "OrderMapper.xml", "hash-1", "OrderMapper", "ns", "findOne");
        ObjectMapper objectMapper = MapperSemanticJsonPublisher.defaultObjectMapper();
        new MapperSemanticJsonPublisher(objectMapper).publish(target, previous);

        AtomicInteger calls = new AtomicInteger();
        MapperSemanticJsonPublisher publisher = new MapperSemanticJsonPublisher(objectMapper, (temporary, dest) -> {
            calls.incrementAndGet();
            throw new java.io.IOException("move failed");
        });
        MapperSemanticExtractionException ex = assertThrows(
                MapperSemanticExtractionException.class,
                () -> publisher.publish(
                        target,
                        MapperSemanticFixtures.singleSelect("OrderMapper.xml", "hash-2", "OrderMapper", "ns", "findOne")));
        assertEquals(MapperSemanticFailureType.WRITE_ERROR, ex.failureType());
        assertEquals(1, calls.get());
        assertEquals(previous, objectMapper.readValue(target.toFile(), MapperSemantic.class));
        assertTrue(listTempFiles().isEmpty());
        assertFalse(Files.list(tempDir)
                .anyMatch(path -> path.getFileName().toString().contains(".tmp")));
    }

    @Test
    void outputMappingPreservesDirectoriesAndAvoidsCollisions() {
        MapperSemanticOutputMapper mapper = new MapperSemanticOutputMapper();
        Path output = tempDir.resolve("out");
        assertEquals(
                output.resolve("module-a/mapper/order/OrderMapper.semantic.json").normalize(),
                mapper.map(output, "module-a/mapper/order/OrderMapper.xml"));
        Path first = mapper.map(output, "module-a/mapper/UserMapper.xml");
        Path second = mapper.map(output, "module-b/mapper/UserMapper.xml");
        assertEquals(output.resolve("module-a/mapper/UserMapper.semantic.json").normalize(), first);
        assertEquals(output.resolve("module-b/mapper/UserMapper.semantic.json").normalize(), second);
        assertFalse(first.equals(second));
        assertThrows(
                MapperSemanticExtractionException.class,
                () -> mapper.map(output, "../escape.xml"));
    }

    private java.util.List<Path> listTempFiles() throws Exception {
        try (Stream<Path> stream = Files.walk(tempDir)) {
            return stream.filter(path -> path.getFileName().toString().contains(".tmp")).toList();
        }
    }
}
