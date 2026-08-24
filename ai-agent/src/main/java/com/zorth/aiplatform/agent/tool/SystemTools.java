package com.zorth.aiplatform.agent.tool;

import com.zorth.aiplatform.agent.model.SystemInfo;
import com.zorth.aiplatform.agent.support.ToolExecutionSupport;
import java.util.Objects;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;

public final class SystemTools {

    private final SystemInfo systemInfo;
    private final ToolExecutionSupport executionSupport;

    public SystemTools(SystemInfo systemInfo, ToolExecutionSupport executionSupport) {
        this.systemInfo = Objects.requireNonNull(systemInfo, "systemInfo must not be null");
        this.executionSupport = Objects.requireNonNull(executionSupport,
                "executionSupport must not be null");
    }

    @Tool(description = """
            Get basic information about this running AI Platform service, including its application name, environment, and version.
            Use this when the user asks about the current AI Platform service itself or its runtime identity.
            """)
    public SystemInfo getSystemInfo(ToolContext toolContext) {
        return executionSupport.execute("getSystemInfo", toolContext, () -> systemInfo);
    }
}
