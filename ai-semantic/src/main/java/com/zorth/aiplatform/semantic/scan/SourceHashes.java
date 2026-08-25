package com.zorth.aiplatform.semantic.scan;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class SourceHashes {

    private SourceHashes() {
    }

    public static String sha256LowerHex(byte[] originalBytes) {
        Objects.requireNonNull(originalBytes, "originalBytes must not be null");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(originalBytes);
            return HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }
}
