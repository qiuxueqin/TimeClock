package com.timeclock.schedule;

import com.timeclock.schedule.dto.DashboardTodayResponse;
import com.timeclock.schedule.dto.TodayTask;
import com.timeclock.task.TaskScheduleCalculator;
import com.timeclock.task.dto.ScheduleType;
import com.timeclock.task.dto.TaskType;
import com.timeclock.task.dto.TaskView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * S5-BE-01 今日总览聚合：一次返回当前用户全部清单任务的今日状态与摘要。
 *
 * <p>每个任务按自身 IANA 时区（DEC-11）计算今日计划；计划判定复用 {@link TaskScheduleCalculator}；
 * 连续摘要复用 {@link StreakCalculator}。只读聚合，不产生任何打卡事实。
 */
@Service
public class TodayOverviewService {
    private static final String NO_PLAN = "noPlan";
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public TodayOverviewService(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    TodayOverviewService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public DashboardTodayResponse today(String userId) {
        String userTimezone = jdbc.queryForObject("SELECT timezone FROM users WHERE id=?", String.class, userId);
        ZoneId userZone = ZoneId.of(userTimezone == null || userTimezone.isBlank() ? "Asia/Shanghai" : userTimezone);
        LocalDate date = LocalDate.now(clock.withZone(userZone));

        List<TaskRow> rows = jdbc.query(
                "SELECT id,name,description,status,start_date,end_date,schedule_type,timezone,daily_target_count"
                        + " FROM tasks WHERE user_id=? ORDER BY created_at DESC, id DESC",
                (rs, n) -> taskRow(rs), userId);

        Map<String, ItemCounts> counts = new HashMap<>();
        jdbc.query("SELECT li.task_id AS task_id, COUNT(*) AS total,"
                        + " COALESCE(SUM(li.status='completed'),0) AS done, COALESCE(SUM(li.status='pending'),0) AS pending"
                        + " FROM learning_items li JOIN tasks t ON t.id=li.task_id WHERE t.user_id=? GROUP BY li.task_id",
                rs -> {
                    while (rs.next()) {
                        counts.put(rs.getString("task_id"),
                                new ItemCounts(rs.getInt("total"), rs.getInt("done"), rs.getInt("pending")));
                    }
                    return null;
                }, userId);

        Map<String, Map<LocalDate, String>> facts = new HashMap<>();
        jdbc.query("SELECT c.task_id AS task_id, c.checkin_date AS checkin_date, c.status AS status"
                        + " FROM checkins c JOIN tasks t ON t.id=c.task_id WHERE t.user_id=? ORDER BY c.checkin_date",
                rs -> {
                    while (rs.next()) {
                        facts.computeIfAbsent(rs.getString("task_id"), k -> new HashMap<>())
                                .put(rs.getObject("checkin_date", LocalDate.class), rs.getString("status"));
                    }
                    return null;
                }, userId);

        TaskScheduleCalculator schedule = new TaskScheduleCalculator(clock);
        StreakCalculator streaks = new StreakCalculator();
        List<TodayTask> tasks = new ArrayList<>(rows.size());
        int todayCount = 0;
        int completedCount = 0;
        int maxCurrent = 0;
        int maxLongest = 0;
        for (TaskRow row : rows) {
            ZoneId zone = ZoneId.of(row.timezone());
            LocalDate taskToday = LocalDate.now(clock.withZone(zone));
            ItemCounts c = counts.getOrDefault(row.id(), new ItemCounts(0, 0, 0));
            Instant from = taskToday.atStartOfDay(zone).toInstant();
            Instant to = taskToday.plusDays(1).atStartOfDay(zone).toInstant();
            Integer completedToday = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM learning_items WHERE task_id=? AND status='completed'"
                            + " AND completed_at>=? AND completed_at<?",
                    Integer.class, row.id(), from, to);

            TaskScheduleCalculator.Task scheduleTask = new TaskScheduleCalculator.Task(row.status(), row.startDate(),
                    row.endDate(), row.scheduleType(), zone, row.dailyTargetCount());
            // 与 ItemService.today 同口径：目标取每日目标与“剩余未完成 + 今日已完成”的较小值。
            TaskScheduleCalculator.PlanDay plan =
                    schedule.planDay(scheduleTask, taskToday, c.pending() + completedToday);
            String status;
            int plannedCount;
            if (!plan.scheduled()) {
                status = NO_PLAN;
                plannedCount = 0;
            } else {
                plannedCount = plan.plannedCount();
                status = completedToday == 0 ? "notStarted" : completedToday >= plannedCount ? "completed" : "inProgress";
            }
            StreakCalculator.Result streak = streaks.calculate(new StreakCalculator.Facts(
                    row.startDate(), row.endDate(), taskToday, c.pending(),
                    facts.getOrDefault(row.id(), Map.of())));
            boolean ended = "active".equals(row.status()) && row.endDate() != null && row.endDate().isBefore(taskToday);
            TaskView view = new TaskView(row.id(), row.name(), row.description(), TaskType.CHECKLIST, row.status(),
                    row.startDate(), row.endDate(), ScheduleType.DAILY, row.timezone(), row.dailyTargetCount(),
                    ended, c.total(), c.completedTotal());
            if (!NO_PLAN.equals(status)) {
                todayCount++;
                if ("completed".equals(status)) completedCount++;
            }
            maxCurrent = Math.max(maxCurrent, streak.currentStreak());
            maxLongest = Math.max(maxLongest, streak.longestStreak());
            tasks.add(new TodayTask(view, status, completedToday, plannedCount, streak.currentStreak()));
        }
        double completionRate = todayCount == 0 ? 0.0 : (double) completedCount / todayCount;
        return new DashboardTodayResponse(date.toString(), todayCount, completedCount,
                todayCount - completedCount, completionRate, tasks, maxCurrent, maxLongest);
    }

    private TaskRow taskRow(ResultSet rs) throws SQLException {
        return new TaskRow(rs.getString("id"), rs.getString("name"), rs.getString("description"),
                rs.getString("status"), rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class), rs.getString("schedule_type"),
                rs.getString("timezone"), rs.getInt("daily_target_count"));
    }

    private record TaskRow(String id, String name, String description, String status,
                           LocalDate startDate, LocalDate endDate, String scheduleType,
                           String timezone, int dailyTargetCount) {
    }

    private record ItemCounts(int total, int completedTotal, int pending) {
    }
}
