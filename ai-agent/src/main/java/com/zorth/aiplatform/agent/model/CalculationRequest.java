package com.zorth.aiplatform.agent.model;

import java.math.BigDecimal;
import org.springframework.ai.tool.annotation.ToolParam;

public record CalculationRequest(
        @ToolParam(description = "Left operand for the calculation", required = true)
        BigDecimal left,
        @ToolParam(description = "Right operand for the calculation", required = true)
        BigDecimal right,
        @ToolParam(description = "Arithmetic operation: ADD, SUBTRACT, MULTIPLY, or DIVIDE", required = true)
        Operation operation) {
}
