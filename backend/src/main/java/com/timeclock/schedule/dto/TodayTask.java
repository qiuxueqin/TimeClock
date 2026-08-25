package com.timeclock.schedule.dto;

import com.timeclock.task.dto.TaskView;

/**
 * 今日总览中的单任务行（S5-API-01 冻结契约）。
 *
 * <p>status 为按任务时区派生的今日状态：notStarted / inProgress / completed / noPlan；
 * noPlan 覆盖草稿、非计划日与剩余条目为零，此时 plannedCount 恒为 0。
 */
public record TodayTask(TaskView task, String status, int completedCount, int plannedCount, int currentStreak) {
}
