package com.zorth.aiplatform.agent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zorth.aiplatform.agent.exception.ToolExecutionException;
import com.zorth.aiplatform.agent.model.CalculationRequest;
import com.zorth.aiplatform.agent.model.Operation;
import com.zorth.aiplatform.agent.support.ToolContextKeys;
import com.zorth.aiplatform.agent.support.ToolExecutionSupport;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

class CalculatorToolsTest {

    private CalculatorTools calculatorTools;
    private ToolContext toolContext;

    @BeforeEach
    void setUp() {
        calculatorTools = new CalculatorTools(new ToolExecutionSupport());
        toolContext = new ToolContext(Map.of(ToolContextKeys.REQUEST_ID, "calculator-test"));
    }

    @Test
    void adds() {
        assertCalculation("12.5", "7.25", Operation.ADD, new BigDecimal("19.75"));
    }

    @Test
    void subtracts() {
        assertCalculation("12.5", "7.25", Operation.SUBTRACT, new BigDecimal("5.25"));
    }

    @Test
    void multiplies() {
        assertCalculation("12345", "6789", Operation.MULTIPLY, new BigDecimal("83810205"));
    }

    @Test
    void dividesFiniteDecimal() {
        assertCalculation("10", "4", Operation.DIVIDE, new BigDecimal("2.5"));
    }

    @Test
    void dividesNonTerminatingDecimalWithDecimal128() {
        BigDecimal result = calculatorTools.calculate(
                request("1", "3", Operation.DIVIDE), toolContext).result();

        assertEquals(BigDecimal.ONE.divide(new BigDecimal("3"), MathContext.DECIMAL128), result);
    }

    @Test
    void rejectsDivisionByZeroAndCanExecuteLaterRequest() {
        assertThrows(ToolExecutionException.class,
                () -> calculatorTools.calculate(request("10", "0", Operation.DIVIDE), toolContext));

        assertCalculation("2", "3", Operation.ADD, new BigDecimal("5"));
    }

    @Test
    void rejectsMissingRequestFields() {
        assertThrows(ToolExecutionException.class,
                () -> calculatorTools.calculate(null, toolContext));
        assertThrows(ToolExecutionException.class,
                () -> calculatorTools.calculate(
                        new CalculationRequest(null, BigDecimal.ONE, Operation.ADD), toolContext));
        assertThrows(ToolExecutionException.class,
                () -> calculatorTools.calculate(
                        new CalculationRequest(BigDecimal.ONE, null, Operation.ADD), toolContext));
        assertThrows(ToolExecutionException.class,
                () -> calculatorTools.calculate(
                        new CalculationRequest(BigDecimal.ONE, BigDecimal.ONE, null), toolContext));
    }

    private void assertCalculation(
            String left, String right, Operation operation, BigDecimal expected) {
        assertEquals(0, expected.compareTo(calculatorTools.calculate(
                request(left, right, operation), toolContext).result()));
    }

    private static CalculationRequest request(String left, String right, Operation operation) {
        return new CalculationRequest(new BigDecimal(left), new BigDecimal(right), operation);
    }
}
