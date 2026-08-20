package com.timeclock.task.dto;

import java.time.LocalDate;

public record TaskUpdateRequest(
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        ScheduleType scheduleType,
        String timezone,
        Integer dailyTargetCount) {
}
