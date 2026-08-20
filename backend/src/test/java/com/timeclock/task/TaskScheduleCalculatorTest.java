package com.timeclock.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TaskScheduleCalculatorTest {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Test
    void planDayUsesTaskTimezoneAndIncludesStartAndEnd() {
        TaskScheduleCalculator calculator = calculator("2026-08-20T01:00:00Z", SHANGHAI);
        TaskScheduleCalculator.Task task = task("active", LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 20), 3, SHANGHAI);
        assertThat(calculator.planDay(task, 2)).isEqualTo(
                new TaskScheduleCalculator.PlanDay(true, LocalDate.of(2026, 8, 20), 2));
        assertThat(calculator.isPlanDay(task, LocalDate.of(2026, 8, 19), 2)).isFalse();
        assertThat(calculator.isPlanDay(task, LocalDate.of(2026, 8, 21), 2)).isFalse();
    }

    @Test
    void taskTimezoneDeterminesBusinessDateAcrossUtcBoundary() {
        TaskScheduleCalculator calculator = calculator("2026-08-20T01:00:00Z", ZoneId.of("America/Los_Angeles"));
        TaskScheduleCalculator.Task task = task("active", LocalDate.of(2026, 8, 19), null, 2,
                ZoneId.of("America/Los_Angeles"));
        assertThat(calculator.today(task)).isEqualTo(LocalDate.of(2026, 8, 19));
    }

    @ParameterizedTest
    @MethodSource("calendarBoundaries")
    void estimatedCompletionUsesLocalDates(String instant, String zone, LocalDate expected) {
        ZoneId taskZone = ZoneId.of(zone);
        TaskScheduleCalculator calculator = calculator(instant, taskZone);
        TaskScheduleCalculator.Task task = task("active", LocalDate.MIN.plusDays(1), null, 2, taskZone);
        assertThat(calculator.estimatedCompletionDate(task, 4)).contains(expected);
    }

    static Stream<Arguments> calendarBoundaries() {
        return Stream.of(
                Arguments.of("2026-01-31T16:30:00Z", "Asia/Shanghai", LocalDate.of(2026, 2, 2)),
                Arguments.of("2025-12-31T16:30:00Z", "Asia/Shanghai", LocalDate.of(2026, 1, 2)),
                Arguments.of("2024-02-29T12:00:00Z", "UTC", LocalDate.of(2024, 3, 1)),
                Arguments.of("2024-03-10T07:01:00Z", "America/New_York", LocalDate.of(2024, 3, 11)),
                Arguments.of("2024-11-03T06:30:00Z", "America/New_York", LocalDate.of(2024, 11, 4)));
    }

    @Test
    void draftEndedAndEmptyRemainingHaveNoPlan() {
        TaskScheduleCalculator calculator = calculator("2026-08-20T01:00:00Z", SHANGHAI);
        TaskScheduleCalculator.Task draft = task("draft", LocalDate.of(2026, 8, 1), null, 2, SHANGHAI);
        TaskScheduleCalculator.Task ended = task("active", LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 19), 2, SHANGHAI);
        assertThat(calculator.planDay(draft, 2).scheduled()).isFalse();
        assertThat(calculator.planDay(ended, 2).scheduled()).isFalse();
        assertThat(calculator.planDay(task("active", LocalDate.of(2026, 8, 1), null, 2, SHANGHAI), 0).scheduled()).isFalse();
        assertThat(calculator.estimatedCompletionDate(draft, 2)).isEmpty();
        assertThat(calculator.estimatedCompletionDate(ended, 2)).isEmpty();
    }

    @Test
    void estimatedCompletionShrinksFinalDayAndRespectsEndDate() {
        TaskScheduleCalculator calculator = calculator("2026-08-20T01:00:00Z", SHANGHAI);
        TaskScheduleCalculator.Task task = task("active", LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 21), 3, SHANGHAI);
        assertThat(calculator.planDay(task, 1).plannedCount()).isEqualTo(1);
        assertThat(calculator.estimatedCompletionDate(task, 4)).contains(LocalDate.of(2026, 8, 21));
        assertThat(calculator.estimatedCompletionDate(task, 7)).isEmpty();
    }

    private TaskScheduleCalculator calculator(String instant, ZoneId zone) {
        return new TaskScheduleCalculator(Clock.fixed(Instant.parse(instant), zone));
    }

    private TaskScheduleCalculator.Task task(String status, LocalDate start, LocalDate end,
                                              int target, ZoneId zone) {
        return new TaskScheduleCalculator.Task(status, start, end, "daily", zone, target);
    }
}
