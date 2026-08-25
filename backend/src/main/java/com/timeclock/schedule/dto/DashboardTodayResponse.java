package com.timeclock.schedule.dto;

import java.util.List;

/**
 * 今日总览聚合响应（S5-API-01 冻结契约）。
 *
 * <p>date 为用户时区今日（仅展示）；各任务计划日以任务自身时区判定。
 * currentStreak/longestStreak 为全部任务中的最大值；逐任务摘要见 {@link TodayTask}。
 */
public record DashboardTodayResponse(
        String date,
        int todayCount,
        int completedCount,
        int pendingCount,
        double completionRate,
        List<TodayTask> tasks,
        int currentStreak,
        int longestStreak) {
}
