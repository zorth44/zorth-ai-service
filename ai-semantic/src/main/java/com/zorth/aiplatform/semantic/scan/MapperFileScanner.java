package com.zorth.aiplatform.semantic.scan;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class MapperFileScanner {

    public List<MapperCandidate> scan(Path sourceRoot) {
        Path root = SourcePaths.normalizeRoot(Objects.requireNonNull(sourceRoot, "sourceRoot must not be null"));
        if (!Files.isDirectory(root) || !Files.isReadable(root)) {
            throw new IllegalArgumentException("Source root must be a readable directory");
        }
        List<MapperCandidate> candidates = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(root) && (attrs.isSymbolicLink() || Files.isSymbolicLink(dir) || isHidden(root, dir))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isSymbolicLink() || Files.isSymbolicLink(file) || !attrs.isRegularFile()) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (isHidden(root, file)) {
                        return FileVisitResult.CONTINUE;
                    }
                    String fileName = file.getFileName().toString();
                    if (!fileName.endsWith(".xml")) {
                        return FileVisitResult.CONTINUE;
                    }
                    String relativePath = SourcePaths.toRootRelative(root, file);
                    candidates.add(new MapperCandidate(file.toAbsolutePath().normalize(), relativePath));
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        catch (IOException ex) {
            throw new IllegalStateException("Failed to scan Mapper source directory", ex);
        }
        candidates.sort(Comparator.comparing(MapperCandidate::relativePath));
        return List.copyOf(candidates);
    }

    private static boolean isHidden(Path root, Path path) {
        Path relative = SourcePaths.normalizeRoot(root).relativize(path.toAbsolutePath().normalize());
        for (Path segment : relative) {
            String name = segment.toString();
            if (name.startsWith(".")) {
                return true;
            }
        }
        return false;
    }
}
