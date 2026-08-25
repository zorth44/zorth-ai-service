package com.zorth.aiplatform.semantic.generation;

import com.zorth.aiplatform.semantic.ai.MapperSemanticAiClient;
import com.zorth.aiplatform.semantic.exception.MapperSemanticExtractionException;
import com.zorth.aiplatform.semantic.model.MapperSemantic;
import com.zorth.aiplatform.semantic.prompt.MapperSemanticPrompt;
import com.zorth.aiplatform.semantic.prompt.MapperSemanticPromptBuilder;
import com.zorth.aiplatform.semantic.scan.MapperPreflightResult;
import com.zorth.aiplatform.semantic.scan.MapperSourceContent;
import com.zorth.aiplatform.semantic.scan.MapperSourceReader;
import com.zorth.aiplatform.semantic.scan.MapperXmlPreflight;
import com.zorth.aiplatform.semantic.scan.SourcePaths;
import com.zorth.aiplatform.semantic.validation.MapperSemanticValidator;
import java.nio.file.Path;
import java.util.Objects;

public final class MapperSemanticExtractor {

    private final MapperSourceReader sourceReader;
    private final MapperXmlPreflight preflight;
    private final MapperSemanticPromptBuilder promptBuilder;
    private final MapperSemanticAiClient aiClient;
    private final MapperSemanticValidator validator;

    public MapperSemanticExtractor(
            MapperSourceReader sourceReader,
            MapperXmlPreflight preflight,
            MapperSemanticPromptBuilder promptBuilder,
            MapperSemanticAiClient aiClient,
            MapperSemanticValidator validator) {
        this.sourceReader = Objects.requireNonNull(sourceReader, "sourceReader must not be null");
        this.preflight = Objects.requireNonNull(preflight, "preflight must not be null");
        this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder must not be null");
        this.aiClient = Objects.requireNonNull(aiClient, "aiClient must not be null");
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
    }

    public MapperSemantic extract(Path sourceRoot, Path file) {
        MapperSourceContent source = sourceReader.read(sourceRoot, file);
        MapperPreflightResult inventory = preflight.inspect(source.utf8Text());
        String mapperName = SourcePaths.mapperName(source.relativePath());
        MapperSemanticPrompt prompt = promptBuilder.build(
                source.relativePath(),
                mapperName,
                inventory.namespace(),
                source.sourceHash(),
                source.utf8Text());
        MapperSemantic modelResult = aiClient.extract(prompt.systemPrompt(), prompt.userPrompt());
        MapperSemantic publishable = modelResult.withTrustedProvenance(source.sourceHash(), source.relativePath());
        validator.validate(publishable, source.sourceHash(), source.relativePath(), mapperName, inventory);
        return publishable;
    }
}
