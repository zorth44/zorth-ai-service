package com.zorth.aiplatform.server.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ChatPropertiesTest {

    @Test
    void defaultsMatchDocumentedValues() {
        ChatProperties properties = new ChatProperties(20, Duration.ofSeconds(300));
        assertEquals(20, properties.memoryMaxMessages());
        assertEquals(Duration.ofSeconds(300), properties.streamTimeout());
    }

    @Test
    void rejectsNonPositiveMemoryWindow() {
        assertThrows(IllegalArgumentException.class, () -> new ChatProperties(0, Duration.ofSeconds(300)));
        assertThrows(IllegalArgumentException.class, () -> new ChatProperties(-1, Duration.ofSeconds(300)));
    }

    @Test
    void rejectsNonPositiveStreamTimeout() {
        assertThrows(IllegalArgumentException.class, () -> new ChatProperties(20, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new ChatProperties(20, Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class, () -> new ChatProperties(20, null));
    }
}
