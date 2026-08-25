package com.zorth.aiplatform.semantic.report;

import java.util.List;
import java.util.Objects;

public record MapperSemanticGenerationReport(
        int total,
        int success,
        int failed,
        int skipped,
        List<MapperSemanticGenerationFailure> failures) {

    public MapperSemanticGenerationReport {
        Objects.requireNonNull(failures, "failures must not be null");
        failures = List.copyOf(failures);
        if (total != success + failed + skipped) {
            throw new IllegalArgumentException("total must equal success + failed + skipped");
        }
        if (failures.size() != failed) {
            throw new IllegalArgumentException("failures.size() must equal failed");
        }
    }
}
