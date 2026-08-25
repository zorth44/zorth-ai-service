package com.zorth.aiplatform.server.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zorth.aiplatform.semantic.exception.SemanticBatchException;
import com.zorth.aiplatform.semantic.generation.MapperSemanticBatchGuard;
import com.zorth.aiplatform.semantic.generation.MapperSemanticGenerationSettings;
import com.zorth.aiplatform.semantic.generation.MapperSemanticGenerator;
import com.zorth.aiplatform.semantic.report.MapperSemanticFailureType;
import com.zorth.aiplatform.semantic.report.MapperSemanticGenerationFailure;
import com.zorth.aiplatform.semantic.report.MapperSemanticGenerationReport;
import com.zorth.aiplatform.server.config.MapperSemanticProperties;
import com.zorth.aiplatform.server.exception.GlobalExceptionHandler;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = MapperSemanticController.class,
        properties = "semantic.mapper.enabled=true")
@Import({MapperSemanticControllerTest.GuardConfiguration.class, GlobalExceptionHandler.class})
class MapperSemanticControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MapperSemanticGenerator generator;

    @MockitoBean
    private MapperSemanticProperties properties;

    @Test
    void returnsUnwrappedReportAndIgnoresBodyPaths() throws Exception {
        MapperSemanticGenerationSettings settings = new MapperSemanticGenerationSettings(
                Path.of("configured-source").toAbsolutePath(),
                Path.of("configured-output").toAbsolutePath(),
                true,
                204_800);
        when(properties.toSettings()).thenReturn(settings);
        when(generator.generate(settings)).thenReturn(new MapperSemanticGenerationReport(
                5,
                2,
                2,
                1,
                List.of(
                        new MapperSemanticGenerationFailure("a.xml", MapperSemanticFailureType.AI_CALL_ERROR, "AI model invocation failed"),
                        new MapperSemanticGenerationFailure("b.xml", MapperSemanticFailureType.VALIDATION_ERROR, "Statement coverage does not match the Mapper XML"))));

        mockMvc.perform(post("/api/v1/semantic/mappers/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceDirectory\":\"/tmp/evil\",\"outputDirectory\":\"/tmp/evil-out\",\"xml\":\"<mapper/>\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.success").value(2))
                .andExpect(jsonPath("$.failed").value(2))
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.failures.length()").value(2))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(generator).generate(settings);
    }

    @Test
    void returnsSafeBatchErrorForInvalidRoot() throws Exception {
        when(properties.toSettings()).thenReturn(new MapperSemanticGenerationSettings(
                Path.of("missing").toAbsolutePath(), Path.of("out").toAbsolutePath(), true, 204_800));
        when(generator.generate(any())).thenThrow(new SemanticBatchException(
                MapperSemanticGenerator.SOURCE_DIRECTORY_INVALID, "The configured source directory is unavailable"));

        mockMvc.perform(post("/api/v1/semantic/mappers/generate"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEMANTIC_SOURCE_DIRECTORY_INVALID"))
                .andExpect(jsonPath("$.message").value("The configured source directory is unavailable"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Exception"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("<mapper"))));
    }

    @Test
    void concurrentTriggerReturns409AndGuardReleasesAfterFailure() throws Exception {
        MapperSemanticGenerationSettings settings = new MapperSemanticGenerationSettings(
                Path.of("src").toAbsolutePath(), Path.of("out").toAbsolutePath(), true, 204_800);
        when(properties.toSettings()).thenReturn(settings);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch hold = new CountDownLatch(1);
        when(generator.generate(settings)).thenAnswer(invocation -> {
            started.countDown();
            if (!hold.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out");
            }
            throw new SemanticBatchException(
                    MapperSemanticGenerator.SOURCE_DIRECTORY_INVALID, "The configured source directory is unavailable");
        }).thenReturn(new MapperSemanticGenerationReport(0, 0, 0, 0, List.of()));

        Thread first = new Thread(() -> {
            try {
                mockMvc.perform(post("/api/v1/semantic/mappers/generate"));
            }
            catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        first.start();
        org.junit.jupiter.api.Assertions.assertTrue(started.await(5, TimeUnit.SECONDS));

        mockMvc.perform(post("/api/v1/semantic/mappers/generate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEMANTIC_GENERATION_ALREADY_RUNNING"))
                .andExpect(jsonPath("$.message").value("Mapper semantic generation is already running"));

        hold.countDown();
        first.join(5_000);
        verify(generator, timeout(5_000).times(1)).generate(settings);

        mockMvc.perform(post("/api/v1/semantic/mappers/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class GuardConfiguration {
        @Bean
        MapperSemanticBatchGuard mapperSemanticBatchGuard() {
            return new MapperSemanticBatchGuard();
        }
    }
}
