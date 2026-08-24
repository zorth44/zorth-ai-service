package com.zorth.aiplatform.agent.tool;

import com.zorth.aiplatform.agent.exception.ToolExecutionException;
import com.zorth.aiplatform.agent.model.CalculationRequest;
import com.zorth.aiplatform.agent.model.CalculationResult;
import com.zorth.aiplatform.agent.support.ToolExecutionSupport;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public final class CalculatorTools {

    private final ToolExecutionSupport executionSupport;

    public CalculatorTools(ToolExecutionSupport executionSupport) {
        this.executionSupport = Objects.requireNonNull(executionSupport,
                "executionSupport must not be null");
    }

    @Tool(description = """
            Perform deterministic basic arithmetic using decimal numbers.
            Use this when the user requests addition, subtraction, multiplication, or division that should be calculated exactly by the application.
            """)
    public CalculationResult calculate(
            @ToolParam(description = "Required operands and arithmetic operation", required = true)
            CalculationRequest request,
            ToolContext toolContext) {
        return executionSupport.execute("calculate", toolContext, () -> calculate(request));
    }

    private CalculationResult calculate(CalculationRequest request) {
        if (request == null || request.left() == null || request.right() == null
                || request.operation() == null) {
            throw new ToolExecutionException("left, right, and operation are required");
        }

        BigDecimal result = switch (request.operation()) {
            case ADD -> request.left().add(request.right());
            case SUBTRACT -> request.left().subtract(request.right());
            case MULTIPLY -> request.left().multiply(request.right());
            case DIVIDE -> divide(request.left(), request.right());
        };
        return new CalculationResult(result);
    }

    private BigDecimal divide(BigDecimal left, BigDecimal right) {
        if (right.compareTo(BigDecimal.ZERO) == 0) {
            throw new ToolExecutionException("Division by zero is not allowed");
        }
        return left.divide(right, MathContext.DECIMAL128);
    }
}
