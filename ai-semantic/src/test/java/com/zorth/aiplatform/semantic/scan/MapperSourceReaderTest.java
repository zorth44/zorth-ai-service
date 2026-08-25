package com.zorth.aiplatform.semantic.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zorth.aiplatform.semantic.exception.MapperSemanticExtractionException;
import com.zorth.aiplatform.semantic.report.MapperSemanticFailureType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MapperSourceReaderTest {

    private final MapperSourceReader reader = new MapperSourceReader();

    @TempDir
    Path tempDir;

    @Test
    void stripsLeadingBomForPromptTextButHashesOriginalBytes() throws Exception {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] text = "<mapper namespace=\"ns\"/>".getBytes(StandardCharsets.UTF_8);
        byte[] original = new byte[bom.length + text.length];
        System.arraycopy(bom, 0, original, 0, bom.length);
        System.arraycopy(text, 0, original, bom.length, text.length);
        Path file = tempDir.resolve("BomMapper.xml");
        Files.write(file, original);

        MapperSourceContent content = reader.read(tempDir, file);
        assertEquals("<mapper namespace=\"ns\"/>", content.utf8Text());
        assertEquals(SourceHashes.sha256LowerHex(original), content.sourceHash());
        assertEquals("BomMapper.xml", content.relativePath());
    }

    @Test
    void rejectsInvalidUtf8() throws Exception {
        Path file = tempDir.resolve("Invalid.xml");
        Files.write(file, new byte[] {(byte) 0xFF, (byte) 0xFE, 0x00});
        MapperSemanticExtractionException ex =
                assertThrows(MapperSemanticExtractionException.class, () -> reader.read(tempDir, file));
        assertEquals(MapperSemanticFailureType.READ_ERROR, ex.failureType());
        assertTrue(ex.getMessage().contains("UTF-8"));
    }
}
