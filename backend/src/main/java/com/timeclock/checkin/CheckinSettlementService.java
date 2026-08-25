package com.timeclock.checkin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S6-BE-03 漏打结算：按任务时区对已过计划日幂等形成 missed/partial 事实。
 *
 * <p>对每个 active 任务重算窗口 [startDate, min(昨日, endDate)] 内的每个计划日：
 * 完成数按 completed_at 落在该日的条目数重建，计划数取 min(每日目标, 当日结束时仍未完成的条目数)，
 * 与既有打卡行对账——缺行补 missed，不再达标回退 partial（撤销跨日后的局部重算）。
 * makeup 行不可逆（DEC-15），结算永不改写。
 */
@Service
public class CheckinSettlementService {
    private static final Logger log = LoggerFactory.getLogger(CheckinSettlementService.class);

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public CheckinSettlementService(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    CheckinSettlementService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** 每小时扫描（实施计划 S6-BE-03）；单任务失败不阻塞其他任务。 */
    @Scheduled(cron = "0 0 * * * *")
    public void settleScheduled() {
        try {
            int settled = settleAll();
            if (settled > 0) log.info("漏打结算完成 settled={}", settled);
        } catch (Exception e) {
            log.error("漏打结算调度失败", e);
        }
    }

    public int settleAll() {
        List<String[]> tasks = jdbc.query(
                "SELECT id, timezone FROM tasks WHERE status='active'",
                (rs, n) -> new String[] {rs.getString(1), rs.getString(2)});
        int total = 0;
        for (String[] task : tasks) {
            try {
                total += settleTask(task[0]);
            } catch (Exception e) {
                log.error("任务漏打结算失败 task={}", task[0], e);
            }
        }
        return total;
    }

    /** 幂等重算单个任务的全部过期计划日；返回写入（插入或修正）的行数。 */
    @Transactional
    public int settleTask(String taskId) {
        TaskRow task = jdbc.query("SELECT id,status,start_date,end_date,timezone,daily_target_count"
                        + " FROM tasks WHERE id=?", rs -> rs.next() ? row(rs) : null, taskId);
        if (task == null || !"active".equals(task.status())) return 0;
        ZoneId zone = ZoneId.of(task.timezone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate end = today.minusDays(1);
        if (task.endDate() != null && task.endDate().isBefore(end)) end = task.endDate();
        if (end.isBefore(task.startDate())) return 0;

        Map<LocalDate, FactRow> facts = new HashMap<>();
        jdbc.query("SELECT checkin_date,status,planned_count,completed_count FROM checkins"
                        + " WHERE task_id=? AND checkin_date>=? AND checkin_date<=?",
                rs -> {
                    FactRow r = new FactRow(rs.getString("status"), rs.getInt("planned_count"), rs.getInt("completed_count"));
                    facts.put(rs.getObject("checkin_date", LocalDate.class), r);
                }, taskId, task.startDate(), end);

        int written = 0;
        for (LocalDate d = task.startDate(); !d.isAfter(end); d = d.plusDays(1)) {
            Instant dayStart = d.atStartOfDay(zone).toInstant();
            Instant dayEnd = d.plusDays(1).atStartOfDay(zone).toInstant();
            DayAggregate agg = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(created_at < ?),0) AS created_by_end,"
                            + " COALESCE(SUM(completed_at IS NOT NULL AND completed_at < ?),0) AS done_before,"
                            + " COALESCE(SUM(completed_at >= ? AND completed_at < ?),0) AS done_in_day"
                            + " FROM learning_items WHERE task_id=?",
                    (rs, n) -> new DayAggregate(rs.getInt(1), rs.getInt(2), rs.getInt(3)),
                    dayEnd, dayStart, dayStart, dayEnd, taskId);
            int planned = Math.min(task.dailyTargetCount(), agg.createdByEnd() - agg.doneBefore());
            if (planned < 1) continue; // 条目尚未录入或已耗尽：非计划日，不产生事实
            int done = agg.doneInDay();
            FactRow existing = facts.get(d);
            if (existing != null && "makeup".equals(existing.status())) continue; // DEC-15
            String status;
            if (done >= planned) {
                status = "completed";
            } else if (done > 0) {
                status = "partial";
            } else if (existing != null && !"missed".equals(existing.status())) {
                // 曾有活动（completed/partial）后全部撤销归零：回退 partial 而非降级 missed。
                status = "partial";
            } else {
                status = "missed";
            }
            if (existing == null) {
                jdbc.update("INSERT INTO checkins (id,task_id,checkin_date,status,planned_count,completed_count,created_at,updated_at)"
                                + " VALUES (?,?,?,?,?,?,?,?)",
                        UUID.randomUUID().toString(), taskId, d, status, planned, done,
                        Instant.now(clock).truncatedTo(java.time.temporal.ChronoUnit.MICROS),
                        Instant.now(clock).truncatedTo(java.time.temporal.ChronoUnit.MICROS));
                written++;
            } else if (existing.planned() != planned || existing.completed() != done || !existing.status().equals(status)) {
                jdbc.update("UPDATE checkins SET status=?,planned_count=?,completed_count=?,updated_at=?"
                                + " WHERE task_id=? AND checkin_date=?",
                        status, planned, done, Instant.now(clock), taskId, d);
                written++;
            }
        }
        return written;
    }

    private TaskRow row(ResultSet rs) throws SQLException {
        return new TaskRow(rs.getString("id"), rs.getString("status"),
                rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class),
                rs.getString("timezone"), rs.getInt("daily_target_count"));
    }

    private record TaskRow(String id, String status, LocalDate startDate, LocalDate endDate,
                           String timezone, int dailyTargetCount) {
    }

    private record FactRow(String status, int planned, int completed) {
    }

    private record DayAggregate(int createdByEnd, int doneBefore, int doneInDay) {
    }
}
