package com.zorth.aiplatform.semantic.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class MapperFileScannerTest {

    private final MapperFileScanner scanner = new MapperFileScanner();

    @TempDir
    Path tempDir;

    @Test
    void returnsNestedXmlSortedByRelativePath() throws Exception {
        Files.createDirectories(tempDir.resolve("b"));
        Files.createDirectories(tempDir.resolve("a/nested"));
        Files.writeString(tempDir.resolve("b/BetaMapper.xml"), "<mapper/>");
        Files.writeString(tempDir.resolve("a/nested/AlphaMapper.xml"), "<mapper/>");
        Files.writeString(tempDir.resolve("ZedMapper.xml"), "<mapper/>");

        List<String> relative = scanner.scan(tempDir).stream().map(MapperCandidate::relativePath).toList();
        assertEquals(List.of("ZedMapper.xml", "a/nested/AlphaMapper.xml", "b/BetaMapper.xml"), relative);
    }

    @Test
    void selectsOnlyLowercaseXmlSuffix() throws Exception {
        Files.writeString(tempDir.resolve("keep.xml"), "<mapper/>");
        Files.writeString(tempDir.resolve("skip.XML"), "<mapper/>");
        Files.writeString(tempDir.resolve("skip.json"), "{}");
        Files.writeString(tempDir.resolve("Skip.java"), "class Skip {}");

        List<String> relative = scanner.scan(tempDir).stream().map(MapperCandidate::relativePath).toList();
        assertEquals(List.of("keep.xml"), relative);
    }

    @Test
    void excludesHiddenFilesAndDirectories() throws Exception {
        Files.createDirectories(tempDir.resolve(".hidden"));
        Files.writeString(tempDir.resolve(".hidden/HiddenMapper.xml"), "<mapper/>");
        Files.writeString(tempDir.resolve(".HiddenFile.xml"), "<mapper/>");
        Files.createDirectories(tempDir.resolve("visible"));
        Files.writeString(tempDir.resolve("visible/.secret.xml"), "<mapper/>");
        Files.writeString(tempDir.resolve("visible/KeepMapper.xml"), "<mapper/>");

        List<String> relative = scanner.scan(tempDir).stream().map(MapperCandidate::relativePath).toList();
        assertEquals(List.of("visible/KeepMapper.xml"), relative);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void excludesFileAndDirectorySymbolicLinks() throws Exception {
        Path realDir = Files.createDirectories(tempDir.resolve("real"));
        Path linkedTarget = Files.writeString(realDir.resolve("RealMapper.xml"), "<mapper/>");
        Files.createSymbolicLink(tempDir.resolve("link.xml"), linkedTarget);
        Files.writeString(tempDir.resolve("outside.xml"), "<mapper/>");
        Path linkedDirTarget = Files.createTempDirectory("semantic-linked-dir");
        try {
            Files.writeString(linkedDirTarget.resolve("OutsideMapper.xml"), "<mapper/>");
            Files.createSymbolicLink(tempDir.resolve("linked-dir"), linkedDirTarget);
            Files.writeString(tempDir.resolve("KeepMapper.xml"), "<mapper/>");

            List<String> relative = scanner.scan(tempDir).stream().map(MapperCandidate::relativePath).toList();
            assertEquals(List.of("KeepMapper.xml", "outside.xml", "real/RealMapper.xml"), relative);
            assertTrue(relative.stream().noneMatch(path -> path.contains("link") || path.contains("OutsideMapper")));
        }
        finally {
            Files.deleteIfExists(linkedDirTarget.resolve("OutsideMapper.xml"));
            Files.deleteIfExists(linkedDirTarget);
        }
    }

    @Test
    void emptyRootReturnsNoCandidates() {
        assertTrue(scanner.scan(tempDir).isEmpty());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void doesNotTraverseOutsideConfiguredRootThroughSymlink() throws Exception {
        Path outsideRoot = Files.createTempDirectory("semantic-outside");
        try {
            Files.writeString(outsideRoot.resolve("EscapeMapper.xml"), "<mapper/>");
            Files.createSymbolicLink(tempDir.resolve("escape"), outsideRoot);
            Files.writeString(tempDir.resolve("InsideMapper.xml"), "<mapper/>");
            List<String> relative = scanner.scan(tempDir).stream().map(MapperCandidate::relativePath).toList();
            assertEquals(List.of("InsideMapper.xml"), relative);
        }
        finally {
            Files.deleteIfExists(outsideRoot.resolve("EscapeMapper.xml"));
            Files.deleteIfExists(outsideRoot);
        }
    }
}
