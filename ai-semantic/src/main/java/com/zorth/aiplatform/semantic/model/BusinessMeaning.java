package com.zorth.aiplatform.semantic.model;

public record BusinessMeaning(String name, String description, String derivedFrom, double confidence) {

    public static final double MIN_CONFIDENCE = 0.7;

    public boolean meetsConfidenceThreshold() {
        return confidence >= MIN_CONFIDENCE;
    }
}
