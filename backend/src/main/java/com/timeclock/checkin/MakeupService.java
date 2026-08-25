package com.timeclock.checkin;

import com.timeclock.auth.BusinessException;
import com.timeclock.common.IdempotencyService;
import com.timeclock.task.TaskService;
import com.timeclock.task.dto.TaskView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S6-BE-04 补打：为任务时区下过去 3 个自然日（不含今天）的 missed/partial 计划日补齐事实。
 *
 * <p>规则（DEC-10/15、不变量 7）：原因必填；补打标记 makeup，计入完成率但不连接、不修复连续链；
 * 提交后不可编辑、不可撤销——重复提交同幂等键回放首次结果，已 makeup 的日期再次补打稳定拒绝。
 */
@Service
public class MakeupService {

    private final JdbcTemplate jdbc;
    private final TaskService tasks;
    private final IdempotencyService idempotency;
    private final Clock clock;

    @Autowired
    public MakeupService(JdbcTemplate jdbc, TaskService tasks, IdempotencyService idempotency) {
        this(jdbc, tasks, idempotency, Clock.systemUTC());
    }

    MakeupService(JdbcTemplate jdbc, TaskService tasks, IdempotencyService idempotency, Clock clock) {
        this.jdbc = jdbc;
        this.tasks = tasks;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    /** 补打请求体：仅原因必填（契约 MakeupRequest）。 */
    public record MakeupRequest(String reason) {
    }

    @Transactional
    public Map<String, Object> makeup(String userId, String taskId, LocalDate date, String key, MakeupRequest request) {
        TaskView task = tasks.get(userId, taskId);
        // 锁序与完成事务一致：先 tasks 行 X 锁再写幂等键（消除外键 S→X 升级死锁）。
        jdbc.query("SELECT id FROM tasks WHERE id=? AND user_id=? FOR UPDATE",
                r -> { if (!r.next()) throw new BusinessException("TASK_NOT_FOUND", "任务不存在", 404); return null; },
                taskId, userId);
        Map<String, Object> replay = idempotency.begin(userId, taskId, "makeup", key,
                request == null ? Map.of() : Map.of("reason", request.reason() == null ? "" : request.reason()),
                (Class<Map<String, Object>>) (Class<?>) Map.class);
        if (replay != null) return replay;

        String reason = request == null || request.reason() == null ? "" : request.reason().trim();
        if (reason.isEmpty()) throw new BusinessException("MAKEUP_REASON_REQUIRED", "补打原因不能为空", 422);

        if (!"active".equals(task.status())) {
            throw new BusinessException("MAKEUP_DATE_NOT_DUE", "任务未启用，无法补打", 422);
        }
        ZoneId zone = ZoneId.of(task.timezone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        boolean planDay = !date.isBefore(task.startDate()) && (task.endDate() == null || !date.isAfter(task.endDate()));
        if (!planDay) throw new BusinessException("MAKEUP_DATE_NOT_DUE", "该日期不是计划日", 422);
        if (!date.isBefore(today) || date.isBefore(today.minusDays(3))) {
            throw new BusinessException("MAKEUP_DATE_OUT_OF_WINDOW", "只能补打过去 3 个自然日内的漏打日期", 422);
        }

        Fact fact = factOf(taskId, date);
        if (fact != null && "makeup".equals(fact.status())) {
            throw new BusinessException("CHECKIN_ALREADY_MADE_UP", "该日期已补打，补打记录不可修改或撤销", 409);
        }
        if (fact == null) throw new BusinessException("CHECKIN_NOT_FOUND", "该日期没有可补打的漏打记录，请先等待日结结算", 404);
        if ("completed".equals(fact.status())) {
            throw new BusinessException("MAKEUP_DATE_NOT_DUE", "该日期已完成打卡，无需补打", 422);
        }

        Instant now = Instant.now(clock).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        jdbc.update("UPDATE checkins SET status='makeup',completed_count=GREATEST(completed_count,planned_count),"
                        + "makeup_reason=?,updated_at=? WHERE task_id=? AND checkin_date=?",
                reason, now, taskId, date);
        Map<String, Object> response = responseView(taskId, task, zone, date);
        idempotency.complete(userId, taskId, "makeup", key, response);
        return response;
    }

    /** S6-BE-02 日期详情：返回当日打卡视图；无事实且非计划日返回 noPlan 派生态。 */
    public Map<String, Object> detail(String userId, String taskId, LocalDate date) {
        TaskView task = tasks.get(userId, taskId);
        ZoneId zone = ZoneId.of(task.timezone());
        return responseView(taskId, task, zone, date);
    }

    private Map<String, Object> responseView(String taskId, TaskView task, ZoneId zone, LocalDate date) {
        Fact fact = factOf(taskId, date);
        boolean planDay = "active".equals(task.status())
                && !date.isBefore(task.startDate()) && (task.endDate() == null || !date.isAfter(task.endDate()));
        if (fact == null) {
            return view(null, taskId, date, "noPlan", null, null, null, solutionSummary(taskId, zone, date), null);
        }
        String status = planDay || !"noPlan".equals(fact.status()) ? fact.status() : fact.status();
        return view(fact.id(), taskId, date, status, fact.planned(), fact.completed(), fact.makeupReason(),
                solutionSummary(taskId, zone, date), fact.updatedAt());
    }

    private Map<String, Object> view(String id, String taskId, LocalDate date, String status, Integer planned,
                                     Integer completed, String reason, String summary, Instant updatedAt) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", id);
        m.put("taskId", taskId);
        m.put("checkinDate", date.toString());
        m.put("status", status);
        m.put("plannedCount", planned);
        m.put("completedCount", completed);
        m.put("makeupReason", reason);
        m.put("solutionSummary", summary);
        m.put("updatedAt", updatedAt == null ? null : updatedAt.toString());
        return m;
    }

    /** 该日已完成条目题解摘要（截断，不返回全文）。 */
    private String solutionSummary(String taskId, ZoneId zone, LocalDate date) {
        return jdbc.query(
                "SELECT CONCAT(COUNT(*), ' 道题解：', SUBSTRING(GROUP_CONCAT(SUBSTRING(solution_text,1,60) SEPARATOR '；'),1,120))"
                        + " FROM learning_items WHERE task_id=? AND status='completed'"
                        + " AND completed_at>=? AND completed_at<?",
                rs -> rs.next() && rs.getString(1) != null && !rs.getString(1).startsWith("0 ") ? rs.getString(1) : null,
                taskId, date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant());
    }

    private Fact factOf(String taskId, LocalDate date) {
        return jdbc.query("SELECT id,status,planned_count,completed_count,makeup_reason,updated_at"
                        + " FROM checkins WHERE task_id=? AND checkin_date=?",
                rs -> rs.next() ? row(rs) : null, taskId, date);
    }

    private Fact row(ResultSet rs) throws SQLException {
        return new Fact(rs.getString("id"), rs.getString("status"), (Integer) rs.getObject("planned_count"),
                (Integer) rs.getObject("completed_count"), rs.getString("makeup_reason"),
                rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toInstant());
    }

    private record Fact(String id, String status, Integer planned, Integer completed,
                        String makeupReason, Instant updatedAt) {
    }
}
