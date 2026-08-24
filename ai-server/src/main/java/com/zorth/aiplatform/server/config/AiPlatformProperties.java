package com.zorth.aiplatform.server.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ai.platform")
public record AiPlatformProperties(
        @NotBlank String applicationName,
        @NotBlank String environment,
        @NotBlank String version) {
}
