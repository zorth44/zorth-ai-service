package com.zorth.aiplatform.semantic.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceHashesAndPathsTest {

    @TempDir
    Path tempDir;

    @Test
    void hashesOriginalBytesAsLowercaseSha256() {
        byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", SourceHashes.sha256LowerHex(bytes));
        assertTrue(SourceHashes.sha256LowerHex(bytes).chars().noneMatch(Character::isUpperCase));
    }

    @Test
    void hashesIncludeBomBytes() {
        byte[] withBom = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'a'};
        byte[] withoutBom = new byte[] {'a'};
        assertTrue(!SourceHashes.sha256LowerHex(withBom).equals(SourceHashes.sha256LowerHex(withoutBom)));
    }

    @Test
    void relativePathUsesForwardSlashesAndRejectsEscape() throws Exception {
        Path root = tempDir.resolve("src/main/resources").toAbsolutePath();
        Files.createDirectories(root.resolve("mapper/order"));
        Path file = root.resolve("mapper/order/OrderMapper.xml");
        Files.writeString(file, "<mapper/>");

        assertEquals("mapper/order/OrderMapper.xml", SourcePaths.toRootRelative(root, file));
        assertEquals("OrderMapper", SourcePaths.mapperName("mapper/order/OrderMapper.xml"));
        assertThrows(
                IllegalArgumentException.class,
                () -> SourcePaths.toRootRelative(root, tempDir.resolve("outside.xml")));
    }

    @Test
    void absoluteRootIsNotIncludedInRelativePath() throws Exception {
        Path root = tempDir.resolve("workspace/project/src/main/resources");
        Files.createDirectories(root.resolve("mapper"));
        Path file = Files.writeString(root.resolve("mapper/OrderMapper.xml"), "<mapper/>");
        String relative = SourcePaths.toRootRelative(root, file);
        assertEquals("mapper/OrderMapper.xml", relative);
        assertTrue(!relative.contains(root.toString()));
    }
}
