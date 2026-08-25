package com.zorth.aiplatform.server.controller;

import com.zorth.aiplatform.semantic.generation.MapperSemanticBatchGuard;
import com.zorth.aiplatform.semantic.generation.MapperSemanticGenerator;
import com.zorth.aiplatform.semantic.report.MapperSemanticGenerationReport;
import com.zorth.aiplatform.server.config.MapperSemanticProperties;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix = "semantic.mapper", name = "enabled", havingValue = "true")
@RequestMapping("/api/v1/semantic/mappers")
public class MapperSemanticController {

    private final MapperSemanticGenerator generator;
    private final MapperSemanticBatchGuard batchGuard;
    private final MapperSemanticProperties properties;

    public MapperSemanticController(
            MapperSemanticGenerator generator,
            MapperSemanticBatchGuard batchGuard,
            MapperSemanticProperties properties) {
        this.generator = Objects.requireNonNull(generator, "generator must not be null");
        this.batchGuard = Objects.requireNonNull(batchGuard, "batchGuard must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @PostMapping(path = "/generate", produces = MediaType.APPLICATION_JSON_VALUE)
    public MapperSemanticGenerationReport generate() {
        return batchGuard.run(() -> generator.generate(properties.toSettings()));
    }
}
