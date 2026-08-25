package com.zorth.aiplatform.semantic.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zorth.aiplatform.semantic.exception.MapperSemanticExtractionException;
import com.zorth.aiplatform.semantic.generation.MapperSemanticJsonPublisher;
import com.zorth.aiplatform.semantic.model.MapperSemantic;
import com.zorth.aiplatform.semantic.report.MapperSemanticFailureType;
import com.zorth.aiplatform.semantic.support.MapperSemanticFixtures;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

@ExtendWith(MockitoExtension.class)
class SpringAiMapperSemanticAiClientTest {

    @Mock
    private ChatModel chatModel;

    private SpringAiMapperSemanticAiClient client;
    private final ObjectMapper objectMapper = MapperSemanticJsonPublisher.defaultObjectMapper();

    @BeforeEach
    void setUp() {
        lenient().when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        client = new SpringAiMapperSemanticAiClient(ChatClient.builder(chatModel).build());
    }

    @Test
    void returnsTypedEntity() throws Exception {
        MapperSemantic expected = MapperSemanticFixtures.singleSelect(
                "mapper/order/OrderMapper.xml", "abc", "OrderMapper", "ns", "findOne");
        when(chatModel.call(any(Prompt.class))).thenReturn(response(objectMapper.writeValueAsString(expected)));

        MapperSemantic actual = client.extract("system", "user");

        assertEquals(expected, actual);
        verify(chatModel, atLeastOnce()).call(any(Prompt.class));
    }

    @Test
    void translatesNullResponse() {
        when(chatModel.call(any(Prompt.class))).thenReturn(new org.springframework.ai.chat.model.ChatResponse(List.of()));

        MapperSemanticExtractionException ex =
                assertThrows(MapperSemanticExtractionException.class, () -> client.extract("system", "user"));
        assertEquals(MapperSemanticFailureType.STRUCTURED_OUTPUT_ERROR, ex.failureType());
        assertTrue(!ex.getMessage().contains("api-key"));
    }

    @Test
    void translatesProviderFailureWithoutCredentials() {
        RuntimeException failure = new IllegalStateException("Authorization: Bearer secret-token");
        when(chatModel.call(any(Prompt.class))).thenThrow(failure);

        MapperSemanticExtractionException ex =
                assertThrows(MapperSemanticExtractionException.class, () -> client.extract("system", "user"));
        assertEquals(MapperSemanticFailureType.AI_CALL_ERROR, ex.failureType());
        assertEquals("AI model invocation failed", ex.getMessage());
        assertSame(failure, ex.getCause());
        assertTrue(!ex.getMessage().contains("secret-token"));
    }

    @Test
    void translatesExhaustedSchemaValidation() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new StructuredOutputValidationException("schema invalid"));

        MapperSemanticExtractionException ex =
                assertThrows(MapperSemanticExtractionException.class, () -> client.extract("system", "user"));
        assertEquals(MapperSemanticFailureType.STRUCTURED_OUTPUT_ERROR, ex.failureType());
        assertTrue(!ex.getMessage().contains("Bearer"));
        verify(chatModel, atLeastOnce()).call(any(Prompt.class));
    }

    @Test
    void adapterSourceDoesNotUseContentOrNativeStructuredOutputOrRetryLoop() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/zorth/aiplatform/semantic/ai/SpringAiMapperSemanticAiClient.java"));
        assertTrue(source.contains("validateSchema()"));
        assertTrue(!source.contains("useProviderStructuredOutput"));
        assertTrue(!source.contains(".content()"));
        assertTrue(!source.contains("temperature"));
        assertTrue(!source.contains("api-key") && !source.contains("apiKey") && !source.contains("API_KEY"));
        assertTrue(!source.contains("maxAttempts"));
        assertTrue(!source.contains("retry"));
    }

    private static org.springframework.ai.chat.model.ChatResponse response(String content) {
        return new org.springframework.ai.chat.model.ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }

    static final class StructuredOutputValidationException extends RuntimeException {
        StructuredOutputValidationException(String message) {
            super(message);
        }
    }
}
