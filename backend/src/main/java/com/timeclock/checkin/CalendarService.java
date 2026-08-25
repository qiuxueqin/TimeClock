package com.timeclock.checkin;

import com.timeclock.auth.BusinessException;
import com.timeclock.task.TaskService;
import com.timeclock.task.dto.TaskView;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * S6-BE-02 月历：按月返回用户全部或单任务的打卡状态。
 *
 * <p>读取时先对 active 任务执行幂等漏打结算（复用 S6-BE-03），保证窗口内已过计划日的
 * missed/partial 事实已落库；月历本身只读，不产生 completed 之外的新事实。合并视图下
 * 同日多任务取最差状态（missed > partial > makeup > completed）。无事实的日期不返回
 * （noPlan 派生态仅由日期详情接口给出）。归属校验复用 TaskService；跨用户统一 404。
 */
@Service
public class CalendarService {

    private final JdbcTemplate jdbc;
    private final TaskService tasks;
    private final CheckinSettlementService settlement;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public CalendarService(JdbcTemplate jdbc, TaskService tasks, CheckinSettlementService settlement) {
        this(jdbc, tasks, settlement, Clock.systemUTC());
    }

    CalendarService(JdbcTemplate jdbc, TaskService tasks, CheckinSettlementService settlement, Clock clock) {
        this.jdbc = jdbc;
        this.tasks = tasks;
        this.settlement = settlement;
        this.clock = clock;
    }

    /** 返回该月有打卡事实或计划日结算事实的日期，升序；filter 可按状态筛选。 */
    public List<Map<String, Object>> month(String userId, String month, String taskId, String filter) {
        YearMonth ym = parseMonth(month);
        if (filter != null && !filter.equals("all") && !filter.equals("completed")
                && !filter.equals("partial") && !filter.equals("missed") && !filter.equals("makeup")) {
            throw new BusinessException("VALIDATION_ERROR", "筛选状态无效", 422);
        }
        List<TaskView> scope;
        if (taskId == null) {
            List<String> ids = jdbc.queryForList("SELECT id FROM tasks WHERE user_id=?", String.class, userId);
            scope = new ArrayList<>(ids.size());
            for (String id : ids) scope.add(tasks.get(userId, id));
        } else {
            scope = List.of(tasks.get(userId, taskId)); // 归属校验；跨用户/不存在统一 TASK_NOT_FOUND
        }
        for (TaskView task : scope) {
            if ("active".equals(task.status())) settlement.settleTask(task.id());
        }

        LocalDate firstDay = ym.atDay(1);
        LocalDate lastDay = ym.atEndOfMonth();
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT c.checkin_date, c.status, c.planned_count, c.completed_count, c.makeup_reason"
                        + " FROM checkins c JOIN tasks t ON t.id=c.task_id WHERE t.user_id=?");
        args.add(userId);
        if (taskId != null) {
            sql.append(" AND c.task_id=?");
            args.add(taskId);
        }
        sql.append(" AND c.checkin_date>=? AND c.checkin_date<=?");
        args.add(firstDay);
        args.add(lastDay);

        Map<LocalDate, DayAggregate> byDate = new HashMap<>();
        jdbc.query(sql.toString(), rs -> {
            byDate.computeIfAbsent(rs.getObject("checkin_date", LocalDate.class), k -> new DayAggregate())
                    .add(rs.getString("status"), rs.getInt("planned_count"),
                            rs.getInt("completed_count"), rs.getString("makeup_reason"));
        }, args.toArray());

        List<Map<String, Object>> days = new ArrayList<>();
        byDate.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            DayAggregate agg = e.getValue();
            String status = agg.status();
            if (filter != null && !"all".equals(filter) && !filter.equals(status)) return;
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", e.getKey().toString());
            day.put("status", status);
            day.put("plannedCount", agg.plannedTotal);
            day.put("completedCount", agg.completedTotal);
            day.put("makeupReason", agg.makeupReason);
            days.add(day);
        });
        return days;
    }

    private YearMonth parseMonth(String month) {
        if (month == null || !month.matches("\\d{4}-\\d{2}")) {
            throw new BusinessException("VALIDATION_ERROR", "month 必须为 YYYY-MM 格式", 422);
        }
        try {
            return YearMonth.parse(month);
        } catch (java.time.format.DateTimeParseException e) {
            throw new BusinessException("VALIDATION_ERROR", "month 必须为有效月份", 422);
        }
    }

    /** 同日多任务合并：missed > partial > makeup > completed；计数取总和，makeup 原因任取其一。 */
    private static final class DayAggregate {
        private boolean missed;
        private boolean partial;
        private boolean makeup;
        private int plannedTotal;
        private int completedTotal;
        private String makeupReason;

        void add(String status, int plannedCount, int completedCount, String reason) {
            switch (status) {
                case "missed" -> missed = true;
                case "partial" -> partial = true;
                case "makeup" -> {
                    makeup = true;
                    makeupReason = reason;
                }
                default -> { /* completed */ }
            }
            plannedTotal += plannedCount;
            completedTotal += completedCount;
        }

        String status() {
            if (missed) return "missed";
            if (partial) return "partial";
            if (makeup) return "makeup";
            return "completed";
        }
    }
}
