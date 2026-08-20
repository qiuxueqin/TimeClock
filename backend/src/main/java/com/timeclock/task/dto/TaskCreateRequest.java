package com.timeclock.task.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record TaskCreateRequest(
        @NotBlank @Size(max = 50) String name,
        @Size(max = 500) String description,
        @NotNull TaskType type,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @NotNull ScheduleType scheduleType,
        @NotBlank String timezone,
        @NotNull @Min(1) Integer dailyTargetCount) {
}
