package com.zorth.aiplatform.semantic.ai;

import com.zorth.aiplatform.semantic.exception.MapperSemanticExtractionException;
import com.zorth.aiplatform.semantic.model.MapperSemantic;
import com.zorth.aiplatform.semantic.report.MapperSemanticFailureType;
import java.util.Objects;
import org.springframework.ai.chat.client.ChatClient;

public final class SpringAiMapperSemanticAiClient implements MapperSemanticAiClient {

    private final ChatClient chatClient;

    public SpringAiMapperSemanticAiClient(ChatClient chatClient) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient must not be null");
    }

    @Override
    public MapperSemantic extract(String systemPrompt, String userPrompt) {
        Objects.requireNonNull(systemPrompt, "systemPrompt must not be null");
        Objects.requireNonNull(userPrompt, "userPrompt must not be null");
        try {
            MapperSemantic result = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .entity(MapperSemantic.class, options -> options.validateSchema());
            if (result == null) {
                throw new MapperSemanticExtractionException(
                        MapperSemanticFailureType.STRUCTURED_OUTPUT_ERROR,
                        "The model returned no structured Mapper semantic result");
            }
            return result;
        }
        catch (MapperSemanticExtractionException ex) {
            throw ex;
        }
        catch (RuntimeException ex) {
            if (isStructuredOutputFailure(ex)) {
                throw new MapperSemanticExtractionException(
                        MapperSemanticFailureType.STRUCTURED_OUTPUT_ERROR,
                        "Structured Mapper semantic output is invalid",
                        ex);
            }
            throw new MapperSemanticExtractionException(
                    MapperSemanticFailureType.AI_CALL_ERROR,
                    "AI model invocation failed",
                    ex);
        }
    }

    private static boolean isStructuredOutputFailure(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            String name = current.getClass().getName();
            if (name.contains("StructuredOutput") || name.contains("SchemaValidation")) {
                return true;
            }
        }
        return false;
    }
}
