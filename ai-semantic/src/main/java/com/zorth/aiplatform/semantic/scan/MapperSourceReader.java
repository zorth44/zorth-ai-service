package com.zorth.aiplatform.semantic.scan;

import com.zorth.aiplatform.semantic.exception.MapperSemanticExtractionException;
import com.zorth.aiplatform.semantic.report.MapperSemanticFailureType;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

public final class MapperSourceReader {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    public MapperSourceContent read(Path sourceRoot, Path file) {
        Objects.requireNonNull(sourceRoot, "sourceRoot must not be null");
        Objects.requireNonNull(file, "file must not be null");
        byte[] originalBytes;
        try {
            originalBytes = Files.readAllBytes(file);
        }
        catch (IOException ex) {
            throw new MapperSemanticExtractionException(
                    MapperSemanticFailureType.READ_ERROR, "The Mapper file could not be read", ex);
        }
        String relativePath;
        try {
            relativePath = SourcePaths.toRootRelative(sourceRoot, file);
        }
        catch (IllegalArgumentException ex) {
            throw new MapperSemanticExtractionException(
                    MapperSemanticFailureType.READ_ERROR, "The Mapper file is outside the configured root", ex);
        }
        String text = decodeUtf8(stripLeadingBom(originalBytes));
        return new MapperSourceContent(
                originalBytes, text, SourceHashes.sha256LowerHex(originalBytes), relativePath);
    }

    static byte[] stripLeadingBom(byte[] originalBytes) {
        if (hasLeadingBom(originalBytes)) {
            return Arrays.copyOfRange(originalBytes, UTF8_BOM.length, originalBytes.length);
        }
        return originalBytes;
    }

    static boolean hasLeadingBom(byte[] originalBytes) {
        return originalBytes.length >= UTF8_BOM.length
                && originalBytes[0] == UTF8_BOM[0]
                && originalBytes[1] == UTF8_BOM[1]
                && originalBytes[2] == UTF8_BOM[2];
    }

    static String decodeUtf8(byte[] bytes) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        }
        catch (CharacterCodingException ex) {
            throw new MapperSemanticExtractionException(
                    MapperSemanticFailureType.READ_ERROR, "The Mapper file is not valid UTF-8", ex);
        }
    }
}
