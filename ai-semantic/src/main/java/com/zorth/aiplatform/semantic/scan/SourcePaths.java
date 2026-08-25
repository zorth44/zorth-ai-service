package com.zorth.aiplatform.semantic.scan;

import java.nio.file.Path;
import java.util.Objects;

public final class SourcePaths {

    private SourcePaths() {
    }

    public static Path normalizeRoot(Path root) {
        Objects.requireNonNull(root, "root must not be null");
        return root.toAbsolutePath().normalize();
    }

    public static String toRootRelative(Path root, Path file) {
        Path normalizedRoot = normalizeRoot(root);
        Path normalizedFile = Objects.requireNonNull(file, "file must not be null")
                .toAbsolutePath()
                .normalize();
        if (!normalizedFile.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Path is outside the configured root");
        }
        String relative = normalizedRoot.relativize(normalizedFile).toString().replace('\\', '/');
        if (relative.startsWith("../") || relative.equals("..") || relative.contains("/../")) {
            throw new IllegalArgumentException("Path is outside the configured root");
        }
        return relative;
    }

    public static String mapperName(String relativePath) {
        Objects.requireNonNull(relativePath, "relativePath must not be null");
        String fileName = relativePath.substring(relativePath.lastIndexOf('/') + 1);
        if (fileName.endsWith(".xml")) {
            return fileName.substring(0, fileName.length() - 4);
        }
        return fileName;
    }
}
