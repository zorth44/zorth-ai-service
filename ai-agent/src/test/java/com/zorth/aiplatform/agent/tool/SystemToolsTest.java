package com.zorth.aiplatform.agent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zorth.aiplatform.agent.model.SystemInfo;
import com.zorth.aiplatform.agent.support.ToolContextKeys;
import com.zorth.aiplatform.agent.support.ToolExecutionSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

class SystemToolsTest {

    @Test
    void returnsSuppliedConfigurationValues() {
        SystemInfo configured = new SystemInfo("configured-app", "staging", "2.4.1");
        SystemTools systemTools = new SystemTools(configured, new ToolExecutionSupport());

        SystemInfo result = systemTools.getSystemInfo(
                new ToolContext(Map.of(ToolContextKeys.REQUEST_ID, "system-test")));

        assertEquals(configured, result);
    }
}
