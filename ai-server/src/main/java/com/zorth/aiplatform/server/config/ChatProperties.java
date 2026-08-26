package com.zorth.aiplatform.server.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "ai.chat")
public record ChatProperties(
        @DefaultValue("20") int memoryMaxMessages,
        @DefaultValue("300s") Duration streamTimeout) {

    public ChatProperties {
        if (memoryMaxMessages < 1) {
            throw new IllegalArgumentException("ai.chat.memory-max-messages must be at least 1");
        }
        if (streamTimeout == null || streamTimeout.isZero() || streamTimeout.isNegative()) {
            throw new IllegalArgumentException("ai.chat.stream-timeout must be positive");
        }
    }
}
