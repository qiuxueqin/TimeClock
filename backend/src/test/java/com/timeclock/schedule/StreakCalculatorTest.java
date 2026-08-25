package com.timeclock.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * TEST-S5-BE-01-01（表驱动单元）：连续天数规则（不变量 7/8）。
 * 仅 completed 计入；无计划日跳过不断链；partial/missed 中断；
 * makeup 不计且不修复；今天未完成不计数也不断链。
 */
class StreakCalculatorTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final StreakCalculator calculator = new StreakCalculator();

    private StreakCalculator.Facts facts(LocalDate start, LocalDate end, LocalDate today,
                                         int pending, Map<LocalDate, String> checkins) {
        return new StreakCalculator.Facts(start, end, today, pending, checkins);
    }

    @Test
    void consecutiveCompletedDaysCountAsStreak() {
        LocalDate today = LocalDate.of(2026, 8, 24);
        var result = calculator.calculate(facts(today.minusDays(2), null, today, 1,
                Map.of(today.minusDays(2), "completed", today.minusDays(1), "completed", today, "completed")));
        assertEquals(3, result.currentStreak());
        assertEquals(3, result.longestStreak());
    }

    @Test
    void todayPlannedButNotCompletedNeitherCountsNorBreaksChain() {
        LocalDate today = LocalDate.of(2026, 8, 24);
        var result = calculator.calculate(facts(today.minusDays(2), null, today, 1,
                Map.of(today.minusDays(2), "completed", today.minusDays(1), "completed")));
        assertEquals(2, result.currentStreak());
        assertEquals(2, result.longestStreak());
    }

    @Test
    void todayPartialDoesNotBreakPriorChain() {
        LocalDate today = LocalDate.of(2026, 8, 24);
        var result = calculator.calculate(facts(today.minusDays(1), null, today, 2,
                Map.of(today.minusDays(1), "completed", today, "partial")));
        assertEquals(1, result.currentStreak());
        assertEquals(1, result.longestStreak());
    }

    @Test
    void missedDayBreaksChain() {
        LocalDate today = LocalDate.of(2026, 8, 24);
        var result = calculator.calculate(facts(today.minusDays(3), null, today, 1,
                Map.of(today.minusDays(3), "completed", today.minusDays(2), "missed",
                        today.minusDays(1), "completed", today, "completed")));
        assertEquals(2, result.currentStreak());
        assertEquals(2, result.longestStreak());
    }

    @Test
    void absentPastPlanDayBreaksChainUntilSettled() {
        LocalDate today = LocalDate.of(2026, 8, 24);
        var result = calculator.calculate(facts(today.minusDays(2), null, today, 1,
                Map.of(today.minusDays(2), "completed", today, "completed")));
        assertEquals(1, result.currentStreak());
        assertEquals(1, result.longestStreak());
    }

    @Test
    void makeupNeitherCountsNorRepairsChain() {
        LocalDate today = LocalDate.of(2026, 8, 24);
        var result = calculator.calculate(facts(today.minusDays(3), null, today, 1,
                Map.of(today.minusDays(3), "completed", today.minusDays(2), "completed",
                        today.minusDays(1), "makeup", today, "completed")));
        assertEquals(1, result.currentStreak());
        assertEquals(2, result.longestStreak());
    }

    @Test
    void daysBeforeStartDateAreNoPlanDaysAndDoNotBreakChain() {
        LocalDate start = LocalDate.of(2026, 8, 23);
        LocalDate today = LocalDate.of(2026, 8, 25);
        var result = calculator.calculate(facts(start, null, today, 1,
                Map.of(start, "completed", start.plusDays(1), "completed", today, "completed")));
        assertEquals(3, result.currentStreak());
        assertEquals(3, result.longestStreak());
    }

    @Test
    void daysAfterEndDateStopTheChainWithoutBreakingIt() {
        LocalDate start = LocalDate.of(2026, 8, 20);
        LocalDate end = LocalDate.of(2026, 8, 22);
        LocalDate today = LocalDate.of(2026, 8, 30);
        var result = calculator.calculate(facts(start, end, today, 0,
                Map.of(start, "completed", start.plusDays(1), "completed", end, "completed")));
        // 剩余为零且今天已过结束日：链收敛到最后一个事实日，保持最终连续而不误报断链。
        assertEquals(3, result.currentStreak());
        assertEquals(3, result.longestStreak());
    }

    @Test
    void exhaustedTailAfterLastCompletionIsSkippedWhenNothingRemains() {
        LocalDate today = LocalDate.of(2026, 8, 24);
        var result = calculator.calculate(facts(today.minusDays(6), null, today, 0,
                Map.of(today.minusDays(6), "completed", today.minusDays(5), "completed")));
        assertEquals(2, result.currentStreak());
        assertEquals(2, result.longestStreak());
    }

    @Test
    void trailingMissedTailStillBreaksWhenItemsRemain() {
        LocalDate today = LocalDate.of(2026, 8, 24);
        var result = calculator.calculate(facts(today.minusDays(6), null, today, 1,
                Map.of(today.minusDays(6), "completed", today.minusDays(5), "completed")));
        assertEquals(0, result.currentStreak());
        assertEquals(2, result.longestStreak());
    }

    @Test
    void longestStreakMayDifferFromCurrentStreak() {
        LocalDate today = LocalDate.of(2026, 8, 24);
        var result = calculator.calculate(facts(today.minusDays(9), null, today, 1,
                Map.of(today.minusDays(9), "completed", today.minusDays(8), "completed",
                        today.minusDays(7), "completed", today.minusDays(6), "completed",
                        today.minusDays(5), "partial",
                        today.minusDays(2), "completed", today.minusDays(1), "completed",
                        today, "completed")));
        assertEquals(3, result.currentStreak());
        assertEquals(4, result.longestStreak());
    }

    @Test
    void noFactsMeansZeroStreaks() {
        LocalDate today = LocalDate.of(2026, 8, 24);
        var result = calculator.calculate(facts(today.minusDays(5), null, today, 3, Map.of()));
        assertEquals(0, result.currentStreak());
        assertEquals(0, result.longestStreak());
    }

    @Test
    void futureTaskNotYetStartedHasZeroStreaks() {
        LocalDate today = LocalDate.of(2026, 8, 24);
        var result = calculator.calculate(facts(today.plusDays(1), null, today, 5, Map.of()));
        assertEquals(0, result.currentStreak());
        assertEquals(0, result.longestStreak());
    }

    @Test
    void crossYearRunAroundNewYearIsContinuous() {
        var result = calculator.calculate(facts(LocalDate.of(2025, 12, 30), null, LocalDate.of(2026, 1, 2), 1,
                Map.of(LocalDate.of(2025, 12, 30), "completed", LocalDate.of(2025, 12, 31), "completed",
                        LocalDate.of(2026, 1, 1), "completed", LocalDate.of(2026, 1, 2), "completed")));
        assertEquals(4, result.currentStreak());
        assertEquals(4, result.longestStreak());
    }

    @Test
    void leapDayRunIsContinuous() {
        var result = calculator.calculate(facts(LocalDate.of(2028, 2, 27), null, LocalDate.of(2028, 3, 1), 1,
                Map.of(LocalDate.of(2028, 2, 27), "completed", LocalDate.of(2028, 2, 28), "completed",
                        LocalDate.of(2028, 2, 29), "completed", LocalDate.of(2028, 3, 1), "completed")));
        assertEquals(4, result.currentStreak());
        assertEquals(4, result.longestStreak());
    }

    /** 夏令时边界：计算基于任务时区的 LocalDate，钟表拨动不影响日期链。 */
    @Test
    void dstTransitionKeepsCalendarDayChain() {
        ZoneId newYork = ZoneId.of("America/New_York");
        LocalDate today = LocalDate.now(java.time.Clock.system(newYork));
        var result = calculator.calculate(facts(today.minusDays(2), null, today, 1,
                Map.of(today.minusDays(2), "completed", today.minusDays(1), "completed", today, "completed")));
        assertEquals(3, result.currentStreak());
    }
}
