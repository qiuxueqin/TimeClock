package com.timeclock.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * TEST-S6-DB-01-01（一致性，远程 MySQL 8）：
 * checkins 唯一 (task_id, checkin_date)、makeup 必须携带原因（V8）、任务删除级联清理打卡。
 */
@SpringBootTest
@ActiveProfiles("test")
class CheckinMigrationTests {

    @Autowired
    private DataSource dataSource;

    private final List<String> createdUserIds = new ArrayList<>();

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement deleteTasks = conn.prepareStatement("DELETE FROM tasks WHERE user_id = ?");
             PreparedStatement deleteUsers = conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
            for (String userId : createdUserIds) {
                deleteTasks.setString(1, userId);
                deleteTasks.executeUpdate();
                deleteUsers.setString(1, userId);
                deleteUsers.executeUpdate();
            }
        }
        createdUserIds.clear();
    }

    /** 同任务同日第二条记录触发唯一约束（不变量 2）。 */
    @Test
    void duplicateTaskDateIsRejectedByUniqueConstraint() throws Exception {
        String taskId = insertTask();
        insertCheckin(taskId, LocalDate.of(2026, 8, 20), "completed", null);
        Throwable duplicate = catchThrowable(() ->
                insertCheckin(taskId, LocalDate.of(2026, 8, 20), "missed", null));
        assertThat(duplicate).as("同任务同日重复打卡应触发唯一约束")
                .isInstanceOf(SQLException.class)
                .satisfies(t -> {
                    SQLException e = (SQLException) t;
                    assertThat(e.getErrorCode() == 1062 || "23000".equals(e.getSQLState())).isTrue();
                });
    }

    /** V8：makeup 无原因（NULL 或纯空白）被 CHECK 拒绝；非 makeup 状态无需原因。 */
    @Test
    void makeupWithoutReasonIsRejected() throws Exception {
        String taskId = insertTask();
        Throwable nullReason = catchThrowable(() ->
                insertCheckin(taskId, LocalDate.of(2026, 8, 20), "makeup", null));
        assertThat(nullReason).as("makeup 缺原因应被 V8 CHECK 拒绝").isInstanceOf(SQLException.class);

        String blankTask = insertTask();
        Throwable blankReason = catchThrowable(() ->
                insertCheckin(blankTask, LocalDate.of(2026, 8, 20), "makeup", "   "));
        assertThat(blankReason).as("makeup 空白原因应被 V8 CHECK 拒绝").isInstanceOf(SQLException.class);

        insertCheckin(taskId, LocalDate.of(2026, 8, 21), "makeup", "出差漏打");
        assertThat(reasonOf(taskId, LocalDate.of(2026, 8, 21))).isEqualTo("出差漏打");
    }

    /** 非法状态仍被 V7 CHECK 拒绝。 */
    @Test
    void invalidStatusRemainsRejected() throws Exception {
        String taskId = insertTask();
        Throwable thrown = catchThrowable(() ->
                insertCheckin(taskId, LocalDate.of(2026, 8, 22), "archived", null));
        assertThat(thrown).isInstanceOf(SQLException.class);
    }

    /** 任务物理删除级联清理打卡事实。 */
    @Test
    void taskDeleteCascadesCheckins() throws Exception {
        String userId = insertUser();
        String taskId = UUID.randomUUID().toString();
        insertTaskForUser(taskId, userId);
        insertCheckin(taskId, LocalDate.of(2026, 8, 23), "completed", null);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM tasks WHERE id=?")) {
            ps.setString(1, taskId);
            ps.executeUpdate();
        }
        assertThat(checkinCount(taskId)).as("任务删除后打卡应级联删除").isZero();
    }

    // ---------- 夹具 ----------

    private String insertUser() throws SQLException {
        String id = UUID.randomUUID().toString();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO users (id, email, password_hash, timezone, status, created_at, updated_at) "
                             + "VALUES (?, ?, '$argon2id$test', 'Asia/Shanghai', 'active', NOW(6), NOW(6))")) {
            ps.setString(1, id);
            ps.setString(2, "checkin-" + id.substring(0, 8) + "@example.com");
            ps.executeUpdate();
        }
        createdUserIds.add(id);
        return id;
    }

    private String insertTask() throws SQLException {
        String taskId = UUID.randomUUID().toString();
        insertTaskForUser(taskId, insertUser());
        return taskId;
    }

    private void insertTaskForUser(String taskId, String userId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO tasks (id, user_id, name, description, start_date, end_date, "
                             + "task_type, schedule_type, timezone, daily_target_count, status, created_at, updated_at) "
                             + "VALUES (?, ?, ?, NULL, '2026-08-01', NULL, 'checklist', 'daily', 'Asia/Shanghai', 1, "
                             + "'active', NOW(6), NOW(6))")) {
            ps.setString(1, taskId);
            ps.setString(2, userId);
            ps.setString(3, "打卡迁移-" + taskId.substring(0, 8));
            ps.executeUpdate();
        }
    }

    private void insertCheckin(String taskId, LocalDate date, String status, String reason) throws SQLException {
        String sql = "INSERT INTO checkins (id, task_id, checkin_date, status, planned_count, completed_count, "
                + "makeup_reason, created_at, updated_at) VALUES (?, ?, ?, ?, 1, 1, ?, NOW(6), NOW(6))";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, taskId);
            ps.setObject(3, date);
            ps.setString(4, status);
            ps.setString(5, reason);
            ps.executeUpdate();
        }
    }

    private int checkinCount(String taskId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM checkins WHERE task_id=?")) {
            ps.setString(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private String reasonOf(String taskId, LocalDate date) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT makeup_reason FROM checkins WHERE task_id=? AND checkin_date=?")) {
            ps.setString(1, taskId);
            ps.setObject(2, date);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }
}
