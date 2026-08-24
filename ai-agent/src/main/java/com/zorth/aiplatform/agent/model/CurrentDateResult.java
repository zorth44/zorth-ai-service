package com.zorth.aiplatform.agent.model;

import java.time.LocalDate;

public record CurrentDateResult(LocalDate date, String dayOfWeek) {
}
