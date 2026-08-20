package com.timeclock.task.dto;

import java.time.LocalDate;

public record TaskView(
        String id,
        String name,
        String description,
        TaskType type,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        ScheduleType scheduleType,
        String timezone,
        int dailyTargetCount,
        boolean ended,
        int itemCount,
        int completedItemCount) {
}
