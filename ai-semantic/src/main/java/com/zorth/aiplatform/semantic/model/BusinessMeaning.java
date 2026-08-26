package com.zorth.aiplatform.semantic.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record BusinessMeaning(
        @JsonPropertyDescription("Concise name for a supported non-trivial business interpretation.") String name,
        @JsonPropertyDescription("Business interpretation that adds meaning beyond a generic CRUD paraphrase.")
                String description,
        @JsonPropertyDescription("Specific source identifiers, comments, fragments, or predicates supporting the inference.")
                String derivedFrom,
        @JsonPropertyDescription("Inference confidence from 0.7 to 1.0; 0.9 or above requires explicit strong code evidence.")
                double confidence) {

    public static final double MIN_CONFIDENCE = 0.7;

    public boolean meetsConfidenceThreshold() {
        return confidence >= MIN_CONFIDENCE;
    }
}
