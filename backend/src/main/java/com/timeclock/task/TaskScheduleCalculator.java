package com.timeclock.task;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/** Pure daily schedule calculation; it never reads or writes the database. */
public final class TaskScheduleCalculator {
    private final Clock clock;

    public TaskScheduleCalculator(Clock clock) {
        this.clock = clock;
    }

    public LocalDate today(Task task) {
        return LocalDate.now(clock.withZone(task.timezone()));
    }

    public boolean isPlanDay(Task task, LocalDate date, int remainingCount) {
        return planDay(task, date, remainingCount).scheduled();
    }

    public boolean isPlanDay(Task task, int remainingCount) {
        return planDay(task, today(task), remainingCount).scheduled();
    }

    public PlanDay planDay(Task task, int remainingCount) {
        return planDay(task, today(task), remainingCount);
    }

    public PlanDay planDay(Task task, LocalDate date, int remainingCount) {
        boolean eligible = "active".equals(task.status())
                && "daily".equals(task.scheduleType())
                && remainingCount > 0
                && !date.isBefore(task.startDate())
                && (task.endDate() == null || !date.isAfter(task.endDate()));
        return eligible
                ? new PlanDay(true, date, Math.min(task.dailyTargetCount(), remainingCount))
                : new PlanDay(false, date, 0);
    }

    public Optional<LocalDate> estimatedCompletionDate(Task task, int remainingCount) {
        if (!"active".equals(task.status()) || !"daily".equals(task.scheduleType()) || remainingCount <= 0) {
            return Optional.empty();
        }
        LocalDate date = today(task);
        if (task.endDate() != null && date.isAfter(task.endDate())) return Optional.empty();
        if (date.isBefore(task.startDate())) date = task.startDate();
        int remaining = remainingCount;
        while (task.endDate() == null || !date.isAfter(task.endDate())) {
            int planned = planDay(task, date, remaining).plannedCount();
            if (planned > 0) {
                remaining -= planned;
                if (remaining == 0) return Optional.of(date);
            }
            date = date.plusDays(1);
        }
        return Optional.empty();
    }

    public record Task(String status, LocalDate startDate, LocalDate endDate,
                       String scheduleType, ZoneId timezone, int dailyTargetCount) {
    }

    public record PlanDay(boolean scheduled, LocalDate date, int plannedCount) {
    }
}
