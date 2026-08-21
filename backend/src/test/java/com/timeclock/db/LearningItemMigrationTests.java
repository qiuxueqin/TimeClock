package com.timeclock.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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

/** S3-DB-01 学习条目模型数据库集成测试（TEST-S3-DB-01-01）。 */
@SpringBootTest
@ActiveProfiles("test")
class LearningItemMigrationTests {

    @Autowired
    private DataSource dataSource;

    private final List<String> userIds = new ArrayList<>();
    private final List<String> taskIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM information_schema.tables "
                     + "WHERE table_schema = DATABASE() AND table_name = 'learning_items'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).as("V5 应创建 learning_items 表").isEqualTo(1);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement deleteTasks = conn.prepareStatement("DELETE FROM tasks WHERE user_id = ?");
             PreparedStatement deleteUsers = conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
            for (String userId : userIds) {
                deleteTasks.setString(1, userId);
                deleteTasks.executeUpdate();
                deleteUsers.setString(1, userId);
                deleteUsers.executeUpdate();
            }
        }
        userIds.clear();
        taskIds.clear();
    }

    @Test
    void itemTableHasRequiredColumnsAndIndexes() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            for (String column : List.of("task_id", "title", "content", "analysis", "external_url",
                    "sort_order", "status", "solution_text", "completed_at", "created_at", "updated_at")) {
                assertThat(hasColumn(conn, column)).as("缺少 learning_items.%s", column).isTrue();
            }
            assertThat(hasIndex(conn, "uk_learning_items_task_sort_order")).isTrue();
            assertThat(hasIndex(conn, "idx_learning_items_task_status_order")).isTrue();
        }
    }

    @Test
    void duplicateSortOrderWithinTaskIsRejectedButSameOrderAcrossTasksIsAllowed() throws Exception {
        String userId = insertUser();
        String firstTask = insertTask(userId, "任务一");
        String secondTask = insertTask(userId, "任务二");
        insertItem(firstTask, "题目 A", 1, "pending");

        Throwable duplicate = catchThrowable(() -> insertItem(firstTask, "题目 B", 1, "pending"));
        assertThat(duplicate).isInstanceOf(SQLException.class)
                .satisfies(t -> assertThat(((SQLException) t).getErrorCode()).isEqualTo(1062));

        insertItem(secondTask, "题目 B", 1, "pending");
        assertThat(itemCount(secondTask)).isEqualTo(1);
    }

    @Test
    void duplicateTitlesAreAllowedForApplicationLevelDeduplication() throws Exception {
        String userId = insertUser();
        String taskId = insertTask(userId, "重复标题任务");
        insertItem(taskId, "同一个标题", 1, "pending");
        insertItem(taskId, "同一个标题", 2, "pending");
        assertThat(itemCount(taskId)).isEqualTo(2);
    }

    @Test
    void missingTaskIsRejectedAndDeletingTaskCascadesItems() throws Exception {
        String userId = insertUser();
        String taskId = insertTask(userId, "级联任务");
        insertItem(taskId, "待删除题目", 1, "completed");
        String missingTask = UUID.randomUUID().toString();

        Throwable missing = catchThrowable(() -> insertItem(missingTask, "无主题目", 1, "pending"));
        assertThat(missing).isInstanceOf(SQLException.class)
                .satisfies(t -> assertThat(((SQLException) t).getErrorCode()).isEqualTo(1452));

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM tasks WHERE id = ?")) {
            ps.setString(1, taskId);
            ps.executeUpdate();
        }
        assertThat(itemCount(taskId)).isZero();
    }

    @Test
    void statusOutsidePendingOrCompletedAndNonPositiveOrderAreRejected() throws Exception {
        String userId = insertUser();
        String taskId = insertTask(userId, "约束任务");
        Throwable status = catchThrowable(() -> insertItem(taskId, "非法状态", 1, "archived"));
        Throwable order = catchThrowable(() -> insertItem(taskId, "非法序号", 0, "pending"));
        assertThat(status).isInstanceOf(SQLException.class);
        assertThat(order).isInstanceOf(SQLException.class);
    }

    private String insertUser() throws SQLException {
        String id = UUID.randomUUID().toString();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO users "
                     + "(id, email, password_hash, timezone, status, created_at, updated_at) "
                     + "VALUES (?, ?, '$argon2id$test', 'Asia/Shanghai', 'active', NOW(6), NOW(6))")) {
            ps.setString(1, id);
            ps.setString(2, "item-" + id.substring(0, 8) + "@example.com");
            ps.executeUpdate();
        }
        userIds.add(id);
        return id;
    }

    private String insertTask(String userId, String name) throws SQLException {
        String id = UUID.randomUUID().toString();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO tasks "
                     + "(id, user_id, name, start_date, task_type, schedule_type, timezone, "
                     + "daily_target_count, status, created_at, updated_at) "
                     + "VALUES (?, ?, ?, CURRENT_DATE, 'checklist', 'daily', 'Asia/Shanghai', 1, 'draft', NOW(6), NOW(6))")) {
            ps.setString(1, id);
            ps.setString(2, userId);
            ps.setString(3, name);
            ps.executeUpdate();
        }
        taskIds.add(id);
        return id;
    }

    private void insertItem(String taskId, String title, int sortOrder, String status) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO learning_items "
                     + "(id, task_id, title, sort_order, status, created_at, updated_at) "
                     + "VALUES (?, ?, ?, ?, ?, NOW(6), NOW(6))")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, taskId);
            ps.setString(3, title);
            ps.setInt(4, sortOrder);
            ps.setString(5, status);
            ps.executeUpdate();
        }
    }

    private int itemCount(String taskId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM learning_items WHERE task_id = ?")) {
            ps.setString(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private boolean hasColumn(Connection conn, String column) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'learning_items' AND column_name = ?")) {
            ps.setString(1, column);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    private boolean hasIndex(Connection conn, String index) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = 'learning_items' AND index_name = ?")) {
            ps.setString(1, index);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }
}
