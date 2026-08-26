package com.zorth.aiplatform.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class DatabaseAgentSystemPromptTest {

    @Test
    void copilotAnswerShapeIsStableInNamedResource() throws Exception {
        String prompt = new ClassPathResource("prompts/database-agent-system-prompt.txt")
                .getContentAsString(StandardCharsets.UTF_8);

        assertTrue(prompt.contains("fenced code block tagged sql"));
        assertTrue(prompt.contains("Do not paste query result rows into the answer"));
        assertTrue(prompt.contains("full corrected statement, not a diff fragment"));
        assertTrue(prompt.contains("you MAY call executeQuery to verify"));
        assertTrue(prompt.contains("Never execute writes or DDL"));
        assertTrue(prompt.contains("Numeric values in query results may be strings"));
        assertFalse(prompt.contains("Answer from the query result"));
    }
}
