package com.zorth.aiplatform.agent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zorth.aiplatform.agent.exception.ToolExecutionException;
import com.zorth.aiplatform.agent.model.CurrentDateResult;
import com.zorth.aiplatform.agent.support.ToolContextKeys;
import com.zorth.aiplatform.agent.support.ToolExecutionSupport;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

class DateToolsTest {

    private DateTools dateTools;
    private ToolContext toolContext;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-21T12:00:00Z"), ZoneOffset.UTC);
        dateTools = new DateTools(clock, new ToolExecutionSupport());
        toolContext = new ToolContext(Map.of(ToolContextKeys.REQUEST_ID, "date-test"));
    }

    @Test
    void returnsCurrentDateAndDayOfWeekFromClock() {
        CurrentDateResult result = dateTools.getCurrentDate(toolContext);

        assertEquals(LocalDate.of(2026, 8, 21), result.date());
        assertEquals("FRIDAY", result.dayOfWeek());
    }

    @Test
    void returnsPositiveDifference() {
        assertEquals(10, dateTools.calculateDaysBetween(
                LocalDate.of(2026, 8, 21), LocalDate.of(2026, 8, 31), toolContext).days());
    }

    @Test
    void returnsZeroForEqualDates() {
        LocalDate date = LocalDate.of(2026, 8, 21);

        assertEquals(0, dateTools.calculateDaysBetween(date, date, toolContext).days());
    }

    @Test
    void returnsNegativeDifference() {
        assertEquals(-10, dateTools.calculateDaysBetween(
                LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 21), toolContext).days());
    }

    @Test
    void rejectsMissingDatesThroughCommonException() {
        assertThrows(ToolExecutionException.class,
                () -> dateTools.calculateDaysBetween(null, LocalDate.now(), toolContext));
        assertThrows(ToolExecutionException.class,
                () -> dateTools.calculateDaysBetween(LocalDate.now(), null, toolContext));
    }
}
