package com.timeclock.checkin;

import com.timeclock.task.TaskService;
import com.timeclock.task.dto.ScheduleType;
import com.timeclock.task.dto.TaskView;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * S6-BE-02 任务统计：当前/最长连续、条目进度与预计完成日期。
 *
 * <p>连续计算复用纯函数 {@link com.timeclock.schedule.StreakCalculator}（不变量 7/8：
 * 仅 completed 计入、无计划日跳过、partial/missed 中断、makeup 不计不修复）。
 * 预计完成日期复用 {@link com.timeclock.task.TaskScheduleCalculator#estimatedCompletionDate}。
 */
@Service
public class TaskStatsService {

    private final JdbcTemplate jdbc;
    private final TaskService tasks;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public TaskStatsService(JdbcTemplate jdbc, TaskService tasks) {
        this(jdbc, tasks, Clock.systemUTC());
    }

    TaskStatsService(JdbcTemplate jdbc, TaskService tasks, Clock clock) {
        this.jdbc = jdbc;
        this.tasks = tasks;
        this.clock = clock;
    }

    public Map<String, Object> stats(String userId, String taskId) {
        TaskView task = tasks.get(userId, taskId);
        ZoneId zone = ZoneId.of(task.timezone());
        LocalDate today = LocalDate.now(clock.withZone(zone));

        int total = count("SELECT COUNT(*) FROM learning_items WHERE task_id=?", taskId);
        int completedTotal = count("SELECT COUNT(*) FROM learning_items WHERE task_id=? AND status='completed'", taskId);
        int pending = count("SELECT COUNT(*) FROM learning_items WHERE task_id=? AND status='pending'", taskId);

        record Fact(LocalDate date, String status) {}
        List<Fact> facts = jdbc.query(
                "SELECT checkin_date,status FROM checkins WHERE task_id=? ORDER BY checkin_date",
                (rs, n) -> new Fact(rs.getObject("checkin_date", LocalDate.class), rs.getString("status")), taskId);
        Map<LocalDate, String> statusByDate = new HashMap<>();
        facts.forEach(f -> statusByDate.put(f.date(), f.status()));

        com.timeclock.schedule.StreakCalculator.Result streak =
                new com.timeclock.schedule.StreakCalculator().calculate(new com.timeclock.schedule.StreakCalculator.Facts(
                        task.startDate(), task.endDate(), today, pending, statusByDate));

        java.util.Optional<LocalDate> estimated = "active".equals(task.status())
                ? new com.timeclock.task.TaskScheduleCalculator(clock).estimatedCompletionDate(
                        new com.timeclock.task.TaskScheduleCalculator.Task(task.status(), task.startDate(),
                                task.endDate(), ScheduleType.DAILY.value(), zone, task.dailyTargetCount()), pending)
                : java.util.Optional.empty();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("task", task);
        m.put("currentStreak", streak.currentStreak());
        m.put("longestStreak", streak.longestStreak());
        m.put("completedItemCount", completedTotal);
        m.put("totalItemCount", total);
        m.put("remainingItemCount", pending);
        m.put("estimatedCompletionDate", estimated.map(LocalDate::toString).orElse(null));
        return m;
    }

    private int count(String sql, String taskId) {
        Integer n = jdbc.queryForObject(sql, Integer.class, taskId);
        return n == null ? 0 : n;
    }
}
