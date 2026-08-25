package com.zorth.aiplatform.semantic.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class MapperSemanticGenerationReportTest {

    @Test
    void enforcesCountInvariants() {
        MapperSemanticGenerationFailure failure =
                new MapperSemanticGenerationFailure("a.xml", MapperSemanticFailureType.READ_ERROR, "safe");
        MapperSemanticGenerationReport report =
                new MapperSemanticGenerationReport(3, 1, 1, 1, List.of(failure));
        assertEquals(3, report.total());
        assertEquals(1, report.failures().size());
        assertThrows(
                IllegalArgumentException.class,
                () -> new MapperSemanticGenerationReport(2, 1, 1, 1, List.of(failure)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new MapperSemanticGenerationReport(1, 0, 1, 0, List.of()));
    }
}
