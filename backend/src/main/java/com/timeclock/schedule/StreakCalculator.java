package com.timeclock.schedule;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

/**
 * 纯连续天数计算（不变量 7/8，S5 今日摘要与 S6 统计共享）：
 * 仅 completed 计入；无计划日跳过不断链；partial/missed 中断；makeup 不计且不修复；
 * 今天为计划日但尚未完成时不计数也不断链。
 *
 * <p>计划日窗口取 {@code [max(startDate, 首个打卡事实日), min(today, endDate)]}：
 * 首个事实日前不可能存在完成链（连续链必须包含至少一个 completed 打卡）；
 * 剩余条目为零时最后一个事实日之后不再生成计划（无计划尾部，不视为断链）；
 * 窗口内早于今天的缺卡日期按最终将结算为 missed 处理（S6-BE-03），中断连续链。
 */
public final class StreakCalculator {

    /** 单个任务的连续事实输入；日期均按任务时区的 LocalDate 表达。 */
    public record Facts(LocalDate startDate, LocalDate endDate, LocalDate today,
                        int remainingPendingCount, Map<LocalDate, String> checkinStatus) {
    }

    public record Result(int currentStreak, int longestStreak) {
    }

    public Result calculate(Facts f) {
        Optional<LocalDate> firstFact = f.checkinStatus().keySet().stream().min(Comparator.naturalOrder());
        if (firstFact.isEmpty()) return new Result(0, 0);
        LocalDate lastFact = f.checkinStatus().keySet().stream().max(Comparator.naturalOrder()).orElseThrow();
        LocalDate start = f.startDate() != null && f.startDate().isAfter(firstFact.get()) ? f.startDate() : firstFact.get();
        LocalDate end = f.today();
        if (f.endDate() != null && f.endDate().isBefore(end)) end = f.endDate();
        // 剩余为零：最后一个事实日之后不再有计划日，收敛到末次事实避免误报断链。
        if (f.remainingPendingCount() <= 0 && lastFact.isBefore(end)) end = lastFact;
        if (end.isBefore(start)) return new Result(0, 0);

        boolean pendingToday = end.equals(f.today()) && !"completed".equals(f.checkinStatus().get(end));
        int longest = 0;
        int run = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if ("completed".equals(f.checkinStatus().get(d))) {
                run++;
                if (run > longest) longest = run;
            } else {
                run = 0;
            }
        }
        int current = 0;
        LocalDate d = end;
        // 今天是计划日但尚未完成：不计数也不断链，从上一个候选日起继续回溯。
        if (pendingToday) d = d.minusDays(1);
        while (!d.isBefore(start) && "completed".equals(f.checkinStatus().get(d))) {
            current++;
            d = d.minusDays(1);
        }
        return new Result(current, Math.max(longest, current));
    }
}
