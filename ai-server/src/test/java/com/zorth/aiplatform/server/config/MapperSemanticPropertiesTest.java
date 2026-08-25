package com.zorth.aiplatform.server.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class MapperSemanticPropertiesTest {

    @Test
    void defaultsWhenDisabledDoNotRequirePaths() {
        MapperSemanticProperties properties =
                new MapperSemanticProperties(false, null, null, true, DataSize.ofKilobytes(200));
        assertFalse(properties.enabled());
        assertNull(properties.sourceDirectory());
        assertNull(properties.outputDirectory());
        assertTrue(properties.overwrite());
        assertEquals(DataSize.ofKilobytes(200), properties.maxFileSize());
        assertEquals(204_800L, properties.maxFileSize().toBytes());
    }

    @Test
    void enabledRequiresDirectoriesAndNormalizesPaths() {
        Path source = Path.of("src");
        Path output = Path.of("target/semantic-out");
        MapperSemanticProperties properties =
                new MapperSemanticProperties(true, source, output, true, DataSize.ofKilobytes(200));
        assertEquals(source.toAbsolutePath().normalize(), properties.sourceDirectory());
        assertEquals(output.toAbsolutePath().normalize(), properties.outputDirectory());
        assertTrue(properties.toSettings().overwrite());
        assertEquals(204_800L, properties.toSettings().maxFileSizeBytes());
    }

    @Test
    void enabledMissingDirectoryFails() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MapperSemanticProperties(true, null, Path.of("out"), true, DataSize.ofKilobytes(200)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new MapperSemanticProperties(true, Path.of("src"), null, true, DataSize.ofKilobytes(200)));
    }

    @Test
    void rejectsNonPositiveLimit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MapperSemanticProperties(false, null, null, true, DataSize.ofBytes(0)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new MapperSemanticProperties(false, null, null, true, DataSize.ofBytes(-1)));
    }
}
