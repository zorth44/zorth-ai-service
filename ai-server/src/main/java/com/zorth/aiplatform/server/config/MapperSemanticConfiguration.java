package com.zorth.aiplatform.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zorth.aiplatform.semantic.ai.SpringAiMapperSemanticAiClient;
import com.zorth.aiplatform.semantic.generation.MapperSemanticBatchGuard;
import com.zorth.aiplatform.semantic.generation.MapperSemanticExtractor;
import com.zorth.aiplatform.semantic.generation.MapperSemanticGenerator;
import com.zorth.aiplatform.semantic.generation.MapperSemanticJsonPublisher;
import com.zorth.aiplatform.semantic.generation.MapperSemanticOutputMapper;
import com.zorth.aiplatform.semantic.prompt.MapperSemanticPromptBuilder;
import com.zorth.aiplatform.semantic.scan.MapperFileScanner;
import com.zorth.aiplatform.semantic.scan.MapperSourceReader;
import com.zorth.aiplatform.semantic.scan.MapperXmlPreflight;
import com.zorth.aiplatform.semantic.validation.MapperSemanticValidator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "semantic.mapper", name = "enabled", havingValue = "true")
public class MapperSemanticConfiguration {

    @Bean
    MapperFileScanner mapperFileScanner() {
        return new MapperFileScanner();
    }

    @Bean
    MapperXmlPreflight mapperXmlPreflight() {
        return new MapperXmlPreflight();
    }

    @Bean
    MapperSourceReader mapperSourceReader() {
        return new MapperSourceReader();
    }

    @Bean
    MapperSemanticPromptBuilder mapperSemanticPromptBuilder() {
        return new MapperSemanticPromptBuilder();
    }

    @Bean
    SpringAiMapperSemanticAiClient mapperSemanticAiClient(ChatClient chatClient) {
        return new SpringAiMapperSemanticAiClient(chatClient);
    }

    @Bean
    MapperSemanticValidator mapperSemanticValidator() {
        return new MapperSemanticValidator();
    }

    @Bean
    MapperSemanticExtractor mapperSemanticExtractor(
            MapperSourceReader mapperSourceReader,
            MapperXmlPreflight mapperXmlPreflight,
            MapperSemanticPromptBuilder mapperSemanticPromptBuilder,
            SpringAiMapperSemanticAiClient mapperSemanticAiClient,
            MapperSemanticValidator mapperSemanticValidator) {
        return new MapperSemanticExtractor(
                mapperSourceReader,
                mapperXmlPreflight,
                mapperSemanticPromptBuilder,
                mapperSemanticAiClient,
                mapperSemanticValidator);
    }

    @Bean
    MapperSemanticOutputMapper mapperSemanticOutputMapper() {
        return new MapperSemanticOutputMapper();
    }

    @Bean
    MapperSemanticJsonPublisher mapperSemanticJsonPublisher(ObjectProvider<ObjectMapper> objectMapper) {
        return new MapperSemanticJsonPublisher(
                objectMapper.getIfAvailable(MapperSemanticJsonPublisher::defaultObjectMapper));
    }

    @Bean
    MapperSemanticGenerator mapperSemanticGenerator(
            MapperFileScanner mapperFileScanner,
            MapperSemanticExtractor mapperSemanticExtractor,
            MapperSemanticOutputMapper mapperSemanticOutputMapper,
            MapperSemanticJsonPublisher mapperSemanticJsonPublisher) {
        return new MapperSemanticGenerator(
                mapperFileScanner,
                mapperSemanticExtractor,
                mapperSemanticOutputMapper,
                mapperSemanticJsonPublisher);
    }

    @Bean
    MapperSemanticBatchGuard mapperSemanticBatchGuard() {
        return new MapperSemanticBatchGuard();
    }
}
