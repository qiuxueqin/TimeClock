package com.timeclock.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** S2-DB-01 任务模型数据库集成测试（TEST-S2-DB-01-01）。 */
@SpringBootTest
@ActiveProfiles("test")
class TaskMigrationTests {

    @Autowired
    private DataSource dataSource;

    private final List<String> createdUserIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.tables "
                             + "WHERE table_schema = DATABASE() AND table_name = 'tasks'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).as("V4 应创建 tasks 表").isEqualTo(1);
        }
    }

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

    @Test
    void taskTableHasRequiredColumnsAndIndexes() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            assertThat(hasColumn(conn, "tasks", "user_id")).isTrue();
            assertThat(hasColumn(conn, "tasks", "name")).isTrue();
            assertThat(hasColumn(conn, "tasks", "start_date")).isTrue();
            assertThat(hasColumn(conn, "tasks", "end_date")).isTrue();
            assertThat(hasColumn(conn, "tasks", "schedule_type")).isTrue();
            assertThat(hasColumn(conn, "tasks", "timezone")).isTrue();
            assertThat(hasColumn(conn, "tasks", "daily_target_count")).isTrue();
            assertThat(hasColumn(conn, "tasks", "status")).isTrue();
            assertThat(hasIndex(conn, "tasks", "idx_tasks_user_status")).isTrue();
            assertThat(hasIndex(conn, "tasks", "uk_tasks_user_name")).isTrue();
        }
    }

    @Test
    void validDraftTaskCanBeStored() throws Exception {
        String userId = insertUser();
        String taskId = UUID.randomUUID().toString();
        insertTask(taskId, userId, "算法题", 5, "draft", LocalDate.of(2026, 8, 20), null);
        assertThat(taskCount(taskId)).isEqualTo(1);
        assertThat(taskStatus(taskId)).isEqualTo("draft");
    }

    @Test
    void invalidStatusIsRejected() throws Exception {
        String userId = insertUser();
        Throwable thrown = catchThrowable(() -> insertTask(
                UUID.randomUUID().toString(), userId, "非法状态", 1, "paused", LocalDate.now(), null));
        assertConstraintViolation(thrown, "状态只能是 draft 或 active");
    }

    @Test
    void dailyTargetBelowOneIsRejected() throws Exception {
        String userId = insertUser();
        Throwable thrown = catchThrowable(() -> insertTask(
                UUID.randomUUID().toString(), userId, "非法目标", 0, "draft", LocalDate.now(), null));
        assertConstraintViolation(thrown, "每日目标必须至少为 1");
    }

    @Test
    void endDateBeforeStartDateIsRejected() throws Exception {
        String userId = insertUser();
        Throwable thrown = catchThrowable(() -> insertTask(
                UUID.randomUUID().toString(), userId, "非法日期", 1, "draft",
                LocalDate.of(2026, 8, 21), LocalDate.of(2026, 8, 20)));
        assertConstraintViolation(thrown, "结束日期不能早于开始日期");
    }

    @Test
    void sameUserDuplicateNameIsRejectedButDifferentUsersMayReuseName() throws Exception {
        String firstUser = insertUser();
        String secondUser = insertUser();
        insertTask(UUID.randomUUID().toString(), firstUser, "同名任务", 1, "draft", LocalDate.now(), null);

        Throwable duplicate = catchThrowable(() -> insertTask(
                UUID.randomUUID().toString(), firstUser, "同名任务", 1, "draft", LocalDate.now(), null));
        assertThat(duplicate).as("同用户同名任务应触发唯一约束")
                .isInstanceOf(SQLException.class)
                .satisfies(t -> assertThat(isDuplicateKey((SQLException) t)).isTrue());

        insertTask(UUID.randomUUID().toString(), secondUser, "同名任务", 1, "draft", LocalDate.now(), null);
        assertThat(taskCountForUser(secondUser)).isEqualTo(1);
    }

    @Test
    void taskBelongsToUserThroughForeignKey() throws Exception {
        String userId = insertUser();
        String missingUserId = UUID.randomUUID().toString();
        Throwable thrown = catchThrowable(() -> insertTask(
                UUID.randomUUID().toString(), missingUserId, "越权归属", 1, "draft", LocalDate.now(), null));
        assertThat(thrown).as("不存在的 user_id 不得插入任务")
                .isInstanceOf(SQLException.class)
                .satisfies(t -> assertThat(isForeignKey((SQLException) t)).isTrue());
        assertThat(taskCountForUser(userId)).isZero();
    }

    private String insertUser() throws SQLException {
        String id = UUID.randomUUID().toString();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO users (id, email, password_hash, timezone, status, created_at, updated_at) "
                             + "VALUES (?, ?, '$argon2id$test', 'Asia/Shanghai', 'active', NOW(6), NOW(6))")) {
            ps.setString(1, id);
            ps.setString(2, "task-" + id.substring(0, 8) + "@example.com");
            ps.executeUpdate();
        }
        createdUserIds.add(id);
        return id;
    }

    private void insertTask(String taskId, String userId, String name, int target, String status,
                             LocalDate startDate, LocalDate endDate) throws SQLException {
        String sql = "INSERT INTO tasks (id, user_id, name, description, start_date, end_date, "
                + "task_type, schedule_type, timezone, daily_target_count, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, NULL, ?, ?, 'checklist', 'daily', 'Asia/Shanghai', ?, ?, NOW(6), NOW(6))";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, taskId);
            ps.setString(2, userId);
            ps.setString(3, name);
            ps.setObject(4, startDate);
            ps.setObject(5, endDate);
            ps.setInt(6, target);
            ps.setString(7, status);
            ps.executeUpdate();
        }
    }

    private int taskCount(String taskId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM tasks WHERE id = ?")) {
            ps.setString(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int taskCountForUser(String userId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM tasks WHERE user_id = ?")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private String taskStatus(String taskId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT status FROM tasks WHERE id = ?")) {
            ps.setString(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private boolean hasColumn(Connection conn, String table, String column) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?")) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    private boolean hasIndex(Connection conn, String table, String index) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?")) {
            ps.setString(1, table);
            ps.setString(2, index);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    private boolean isDuplicateKey(SQLException e) {
        return e.getErrorCode() == 1062 || "23000".equals(e.getSQLState());
    }

    private boolean isForeignKey(SQLException e) {
        return e.getErrorCode() == 1452 || "23000".equals(e.getSQLState());
    }

    private void assertConstraintViolation(Throwable throwable, String reason) {
        assertThat(throwable).as(reason).isInstanceOf(SQLException.class)
                .satisfies(t -> {
                    SQLException e = (SQLException) t;
                    assertThat(e.getErrorCode() == 3819 || e.getErrorCode() == 4025
                            || "23000".equals(e.getSQLState()))
                            .as("应为 MySQL CHECK/约束错误，实际 code=%s state=%s", e.getErrorCode(), e.getSQLState())
                            .isTrue();
                });
    }
}
