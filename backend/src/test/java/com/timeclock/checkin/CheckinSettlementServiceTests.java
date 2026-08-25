package com.timeclock.checkin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * TEST-S6-BE-03-01（调度与时区，远程 MySQL 8）：
 * 漏打结算按任务时区幂等形成 missed/partial；重复执行与重启安全；makeup 不可改写。
 */
@SpringBootTest
@ActiveProfiles("test")
class CheckinSettlementServiceTests {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired CheckinSettlementService settlement;
    @Autowired JdbcTemplate jdbc;

    private final List<String> userIds = new java.util.ArrayList<>();
    private final List<String> taskIds = new java.util.ArrayList<>();

    @AfterEach
    void cleanup() {
        for (String taskId : taskIds) jdbc.update("DELETE FROM tasks WHERE id=?", taskId);
        for (String userId : userIds) jdbc.update("DELETE FROM users WHERE id=?", userId);
        taskIds.clear();
        userIds.clear();
    }

    /** 无记录过期计划日 → missed；部分完成 → partial；条目未录入的日期不产生事实。 */
    @Test
    void settlesMissedAndPartialFactsByTaskTimezone() {
        LocalDate today = LocalDate.now(ZONE);
        String taskId = createTask(today.minusDays(3), null, 2);

        // 前日录入两条且均未完成 → missed；昨日其中一条完成 → partial。
        seedItem(taskId, "前日题目A", today.minusDays(2).atTime(10, 0), null);
        seedItem(taskId, "前日题目B", today.minusDays(2).atTime(10, 0), null);
        jdbc.update("UPDATE learning_items SET completed_at=? WHERE title='前日题目B'",
                today.minusDays(1).atTime(11, 0).atZone(ZONE).toInstant());

        int written = settlement.settleAll();
        assertTrue(written >= 2, "至少写入两日事实");

        Map<String, Object> missed = checkin(taskId, today.minusDays(2));
        assertEquals("missed", missed.get("status"));
        assertEquals(2, ((Number) missed.get("planned_count")).intValue());
        assertEquals(0, ((Number) missed.get("completed_count")).intValue());

        Map<String, Object> partial = checkin(taskId, today.minusDays(1));
        assertEquals("partial", partial.get("status"));
        assertEquals(2, ((Number) partial.get("planned_count")).intValue());
        assertEquals(1, ((Number) partial.get("completed_count")).intValue());

        // 首日开始日前不产生事实。
        assertNull(checkin(taskId, today.minusDays(3)));

        // 幂等：再次结算不新增行、不改写既有事实。
        int again = settlement.settleTask(taskId);
        assertEquals(0, again);
    }

    /** 午夜前后重复执行（模拟应用重启）：结果稳定，不重复计数、不改写 completed。 */
    @Test
    void repeatedRunsAroundMidnightStayIdempotent() {
        LocalDate today = LocalDate.now(ZONE);
        String taskId = createTask(today.minusDays(2), null, 1);
        seedItem(taskId, "已完成题目", today.minusDays(2).atTime(9, 0), today.minusDays(2).atTime(9, 30));

        settlement.settleTask(taskId); // 当日结束后首次结算（前一日已过）
        settlement.settleTask(taskId); // 模拟午夜后重复扫描
        settlement.settleTask(taskId); // 模拟重启后再扫描

        Map<String, Object> fact = checkin(taskId, today.minusDays(2));
        assertEquals("completed", fact.get("status"));
        assertEquals(1, countCheckins(taskId));
    }

    /** 已有 completed 打卡且数字一致的日期不被结算改写。 */
    @Test
    void doesNotTouchConsistentCompletedFacts() {
        LocalDate today = LocalDate.now(ZONE);
        String taskId = createTask(today.minusDays(1), null, 1);
        seedItem(taskId, "历史完成", today.minusDays(1).atTime(9, 0), today.minusDays(1).atTime(9, 15));
        seedCheckin(taskId, today.minusDays(1), "completed", 1, 1);

        assertEquals(0, settlement.settleTask(taskId));
        assertEquals("completed", checkin(taskId, today.minusDays(1)).get("status"));
    }

    /** makeup 行不可逆：结算不得覆盖补打事实（DEC-15）。 */
    @Test
    void neverOverwritesMakeupFacts() {
        LocalDate today = LocalDate.now(ZONE);
        String taskId = createTask(today.minusDays(1), null, 1);
        seedItem(taskId, "未做题目", today.minusDays(1).atTime(9, 0), null);
        seedCheckin(taskId, today.minusDays(1), "makeup", 1, 0);

        settlement.settleTask(taskId);
        Map<String, Object> fact = checkin(taskId, today.minusDays(1));
        assertEquals("makeup", fact.get("status"));
    }

    /** 草稿任务与尚未开始的 active 任务不产生事实。 */
    @Test
    void skipsDraftAndFutureTasks() {
        LocalDate today = LocalDate.now(ZONE);
        String draft = createTaskWithStatus(today.minusDays(5), null, 1, "draft");
        String future = createTask(today.plusDays(1), null, 1);

        settlement.settleAll();
        assertEquals(0, countCheckins(draft));
        assertEquals(0, countCheckins(future));
    }

    /** 结束日期截断窗口：endDate 之后不再生成事实。 */
    @Test
    void respectsEndDateWindow() {
        LocalDate today = LocalDate.now(ZONE);
        String taskId = createTask(today.minusDays(5), today.minusDays(4), 1);
        seedItem(taskId, "题目", today.minusDays(5).atTime(9, 0), null);

        settlement.settleTask(taskId);
        assertNotNull(checkin(taskId, today.minusDays(5)));
        assertNotNull(checkin(taskId, today.minusDays(4)));
        assertNull(checkin(taskId, today.minusDays(3))); // endDate 之后
    }

    /** 跨时区：同一时刻，纽约任务与上海任务的“昨天”不同。 */
    @Test
    void timezoneAwareWindowsDifferPerTask() {
        Instant now = Instant.now();
        ZonedDateTime shanghai = now.atZone(ZONE);
        ZonedDateTime newYork = now.atZone(ZoneId.of("America/New_York"));
        if (shanghai.toLocalDate().equals(newYork.toLocalDate())) {
            return; // 仅在两地日期不同（上海已跨日）时验证差异边界。
        }
        LocalDate nyToday = newYork.toLocalDate();
        String nyTask = createTaskInZone(nyToday.minusDays(1), null, 1, "America/New_York");
        seedItem(nyTask, "NY 题目", nyToday.minusDays(1).atTime(20, 0)
                .atZone(ZoneId.of("America/New_York")).toInstant(), null);
        settlement.settleTask(nyTask);
        // 纽约的昨日对纽约时区结算为 partial（有创建未达标）；若按上海时区会算成更早日期。
        assertEquals("partial", checkin(nyTask, nyToday.minusDays(1)).get("status"));
    }

    // ---------- 夹具 ----------

    private String createUser() {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO users (id,email,password_hash,timezone,status,created_at,updated_at)"
                        + " VALUES (?,?,'$argon2id$test','Asia/Shanghai','active',NOW(6),NOW(6))",
                id, "settle-" + id.substring(0, 8) + "@example.com");
        userIds.add(id);
        return id;
    }

    private String createTask(LocalDate start, LocalDate end, int target) {
        return createTaskInZone(start, end, target, "Asia/Shanghai");
    }

    private String createTaskWithStatus(LocalDate start, LocalDate end, int target, String status) {
        String userId = createUser();
        String taskId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO tasks (id,user_id,name,description,start_date,end_date,task_type,schedule_type,"
                        + "timezone,daily_target_count,status,created_at,updated_at)"
                        + " VALUES (?,?,?,NULL,?,?,'checklist','daily','Asia/Shanghai',?,?,NOW(6),NOW(6))",
                taskId, userId, "结算任务-" + taskId.substring(0, 6), start, end, target, status);
        taskIds.add(taskId);
        return taskId;
    }

    private String createTaskInZone(LocalDate start, LocalDate end, int target, String zone) {
        String userId = createUser();
        String taskId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO tasks (id,user_id,name,description,start_date,end_date,task_type,schedule_type,"
                        + "timezone,daily_target_count,status,created_at,updated_at)"
                        + " VALUES (?,?,?,NULL,?,?,'checklist','daily',?,?, 'active',NOW(6),NOW(6))",
                taskId, userId, "结算任务-" + taskId.substring(0, 6), start, end, zone, target);
        taskIds.add(taskId);
        return taskId;
    }

    private void seedItem(String taskId, String title, java.time.LocalDateTime created, java.time.LocalDateTime completed) {
        seedItem(taskId, title,
                created.atZone(ZONE).toInstant(),
                completed == null ? null : completed.atZone(ZONE).toInstant());
    }

    private void seedItem(String taskId, String title, Instant created, Instant completed) {
        jdbc.update("INSERT INTO learning_items (id,task_id,title,status,solution_text,sort_order,completed_at,created_at,updated_at)"
                        + " VALUES (?,?,?,'completed','题解',?,?,?,?)",
                UUID.randomUUID().toString(), taskId, title,
                jdbc.queryForObject("SELECT COALESCE(MAX(sort_order),0)+1 FROM learning_items WHERE task_id=?", Integer.class, taskId),
                completed, created, created);
    }

    private void seedCheckin(String taskId, LocalDate date, String status, int planned, int completed) {
        String reason = "makeup".equals(status) ? "补打原因" : null;
        jdbc.update("INSERT INTO checkins (id,task_id,checkin_date,status,planned_count,completed_count,makeup_reason,created_at,updated_at)"
                        + " VALUES (?,?,?,?,?,?,?,NOW(6),NOW(6))",
                UUID.randomUUID().toString(), taskId, date, status, planned, completed, reason);
    }

    private Map<String, Object> checkin(String taskId, LocalDate date) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM checkins WHERE task_id=? AND checkin_date=?", taskId, date);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private int countCheckins(String taskId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM checkins WHERE task_id=?", Integer.class, taskId);
        return n == null ? 0 : n;
    }
}
