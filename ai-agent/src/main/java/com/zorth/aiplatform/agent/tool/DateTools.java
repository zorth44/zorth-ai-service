package com.zorth.aiplatform.agent.tool;

import com.zorth.aiplatform.agent.exception.ToolExecutionException;
import com.zorth.aiplatform.agent.model.CurrentDateResult;
import com.zorth.aiplatform.agent.model.DateDifferenceResult;
import com.zorth.aiplatform.agent.support.ToolExecutionSupport;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public final class DateTools {

    private final Clock clock;
    private final ToolExecutionSupport executionSupport;

    public DateTools(Clock clock, ToolExecutionSupport executionSupport) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.executionSupport = Objects.requireNonNull(executionSupport,
                "executionSupport must not be null");
    }

    @Tool(description = """
            Get the current server date and day of week.
            Use this when the user asks about today or the current date, or when the current date is needed for another task.
            """)
    public CurrentDateResult getCurrentDate(ToolContext toolContext) {
        return executionSupport.execute("getCurrentDate", toolContext, () -> {
            LocalDate currentDate = LocalDate.now(clock);
            return new CurrentDateResult(currentDate, currentDate.getDayOfWeek().name());
        });
    }

    @Tool(description = """
            Calculate the signed number of calendar days from a known start date to a known end date.
            Use this only after both dates are known; a positive result means the end is later, zero means equal, and a negative result means earlier.
            """)
    public DateDifferenceResult calculateDaysBetween(
            @ToolParam(description = "Known start date in ISO-8601 format", required = true)
            LocalDate startDate,
            @ToolParam(description = "Known end date in ISO-8601 format", required = true)
            LocalDate endDate,
            ToolContext toolContext) {
        return executionSupport.execute("calculateDaysBetween", toolContext, () -> {
            if (startDate == null || endDate == null) {
                throw new ToolExecutionException("Both startDate and endDate are required");
            }
            return new DateDifferenceResult(ChronoUnit.DAYS.between(startDate, endDate));
        });
    }
}
