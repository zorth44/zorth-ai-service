package com.zorth.aiplatform.semantic.scan;

public record MapperSourceContent(byte[] originalBytes, String utf8Text, String sourceHash, String relativePath) {
}
