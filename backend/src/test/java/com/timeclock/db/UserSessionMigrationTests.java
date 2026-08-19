package com.timeclock.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * S1-DB-01 用户与会话模型数据库集成测试（TEST-S1-DB-01-01）。
 *
 * <p>在协调者提供的远程 MySQL 8 实例（独立测试库 time_clock_test）上验证：
 * 1. 大小写或空白规范化后相同的邮箱只允许一条有效记录（数据库兜底，不依赖应用层规范化）；
 * 2. 并发插入相同邮箱只有一条成功；
 * 3. 重复会话令牌哈希被拒绝；
 * 4. 表中不保存明文密码或明文会话令牌。
 *
 * <p>每次测试使用独立随机邮箱/令牌，并在 @AfterEach 清理，避免测试间相互干扰。
 * 连接信息仅从环境变量注入（application-test.yml）。
 */
@SpringBootTest
@ActiveProfiles("test")
class UserSessionMigrationTests {

    @Autowired
    private DataSource dataSource;

    /** 已创建邮箱，供清理。 */
    private final List<String> createdEmails = new ArrayList<>();
    /** 已创建会话 ID，供清理。 */
    private final List<String> createdSessionIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        // 确认 V2 迁移已应用：users 与 user_sessions 表存在
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.tables "
                             + "WHERE table_schema = DATABASE() "
                             + "AND table_name IN ('users', 'user_sessions')")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).as("users 与 user_sessions 表应已由 V2 迁移创建").isEqualTo(2);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement delSessions = conn.prepareStatement(
                     "DELETE FROM user_sessions WHERE user_id IN (SELECT id FROM users WHERE email = ?)");
             PreparedStatement delUsers = conn.prepareStatement("DELETE FROM users WHERE email = ?")) {
            for (String email : createdEmails) {
                delSessions.setString(1, email);
                delSessions.executeUpdate();
                delUsers.setString(1, email);
                delUsers.executeUpdate();
            }
        }
    }

    /** 插入一个用户；邮箱未规范化时仍按给定值存储。返回用户 ID。 */
    private String insertUser(String email, String passwordHash) throws SQLException {
        String id = UUID.randomUUID().toString();
        String sql = "INSERT INTO users (id, email, password_hash, timezone, overdue_reminder_visible, "
                + "status, version, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'Asia/Shanghai', 1, 'active', 0, NOW(6), NOW(6))";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, email);
            ps.setString(3, passwordHash);
            ps.executeUpdate();
        }
        createdEmails.add(email);
        return id;
    }

    /** 插入一条会话；返回会话 ID。 */
    private void insertSession(String userId, String tokenHash) throws SQLException {
        String id = UUID.randomUUID().toString();
        String sql = "INSERT INTO user_sessions (id, user_id, token_hash, expires_at, "
                + "last_accessed_at, device_summary, created_at) "
                + "VALUES (?, ?, ?, DATE_ADD(NOW(6), INTERVAL 30 DAY), NOW(6), 'test-device', NOW(6))";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, userId);
            ps.setString(3, tokenHash);
            ps.executeUpdate();
        }
        createdSessionIds.add(id);
    }

    /** 判断某个 SQLException 是否为 MySQL 唯一键冲突（error 1062 / SQLState 23000）。 */
    private boolean isDuplicateKey(SQLException e) {
        return e.getErrorCode() == 1062 || "23000".equals(e.getSQLState());
    }

    // ---- 1. 邮箱唯一：大小写变体 ----

    @Test
    void emailCaseVariantIsRejected() throws Exception {
        String base = "case-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        insertUser(base, "$argon2id$fakeHashA");

        Throwable thrown = catchThrowable(() -> insertUser(base.toUpperCase(), "$argon2id$fakeHashB"));
        assertThat(thrown)
                .as("大小写变体邮箱应触发唯一约束")
                .isInstanceOf(SQLException.class)
                .satisfies(t -> assertThat(isDuplicateKey((SQLException) t)).isTrue());

        assertThat(userCount(base)).as("规范化后同一邮箱应只有一条记录").isEqualTo(1);
    }

    // ---- 2. 邮箱唯一：空白变体（数据库兜底，独立于应用层规范化） ----

    @Test
    void emailWhitespaceVariantIsRejected() throws Exception {
        String base = "ws-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        insertUser(base, "$argon2id$fakeHashA");

        Throwable thrown = catchThrowable(() -> insertUser("  " + base + "  ", "$argon2id$fakeHashB"));
        assertThat(thrown)
                .as("首尾空白变体邮箱应触发唯一约束（email_normalized 生成列兜底）")
                .isInstanceOf(SQLException.class)
                .satisfies(t -> assertThat(isDuplicateKey((SQLException) t)).isTrue());

        // 同时验证大小写+空白混合变体也被拒绝
        Throwable mixed = catchThrowable(() -> insertUser("  " + base.toUpperCase() + "\t", "$argon2id$fakeHashC"));
        assertThat(mixed)
                .as("大小写+空白混合变体邮箱应触发唯一约束")
                .isInstanceOf(SQLException.class)
                .satisfies(t -> assertThat(isDuplicateKey((SQLException) t)).isTrue());

        assertThat(userCount(base)).as("规范化后同一邮箱应只有一条记录").isEqualTo(1);
    }

    // ---- 3. 并发插入相同邮箱只允许一条 ----

    @Test
    void concurrentEmailInsertOnlyOneSucceeds() throws Exception {
        String email = "concurrent-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                final String hash = "$argon2id$fakeHash" + i;
                results.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        insertUser(email, hash);
                        return 1; // 成功
                    } catch (SQLException e) {
                        if (isDuplicateKey(e)) {
                            return 0; // 唯一冲突
                        }
                        throw new IllegalStateException("非预期的 SQL 异常", e);
                    }
                }));
            }
            ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            int success = 0;
            int conflicts = 0;
            for (Future<Integer> f : results) {
                int r = f.get(30, TimeUnit.SECONDS);
                if (r == 1) {
                    success++;
                } else {
                    conflicts++;
                }
            }
            assertThat(success).as("并发插入相同邮箱应恰好一条成功").isEqualTo(1);
            assertThat(conflicts).as("其余并发插入应全部唯一冲突").isEqualTo(threads - 1);
            assertThat(userCount(email)).as("并发后该邮箱应只有一条记录").isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- 4. 重复会话令牌哈希被拒绝 ----

    @Test
    void duplicateSessionTokenHashIsRejected() throws Exception {
        String email = "session-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String userId = insertUser(email, "$argon2id$fakeHashA");

        String tokenHash = sha256hex("raw-session-token-" + UUID.randomUUID());
        insertSession(userId, tokenHash);

        Throwable thrown = catchThrowable(() -> insertSession(userId, tokenHash));
        assertThat(thrown)
                .as("重复会话令牌哈希应触发唯一约束")
                .isInstanceOf(SQLException.class)
                .satisfies(t -> assertThat(isDuplicateKey((SQLException) t)).isTrue());

        assertThat(sessionCount(tokenHash)).as("同一令牌哈希应只有一条记录").isEqualTo(1);
    }

    // ---- 5. 敏感明文不落库 ----

    @Test
    void sensitivePlaintextColumnsDoNotExistAndHashesAreStored() throws Exception {
        // 结构约束：表只含哈希列，不设明文列（password / token），从 schema 层面禁止保存明文。
        try (Connection conn = dataSource.getConnection()) {
            assertThat(hasColumn(conn, "users", "password"))
                    .as("users 表不应存在明文 password 列")
                    .isFalse();
            assertThat(hasColumn(conn, "users", "password_hash"))
                    .as("users 表应存在 password_hash 哈希列")
                    .isTrue();
            assertThat(hasColumn(conn, "user_sessions", "token"))
                    .as("user_sessions 表不应存在明文 token 列")
                    .isFalse();
            assertThat(hasColumn(conn, "user_sessions", "token_hash"))
                    .as("user_sessions 表应存在 token_hash 哈希列")
                    .isTrue();
        }

        // 写入的哈希值具备哈希形态，且不是输入明文本身。
        String email = "plaintext-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String plainPassword = "SuperSecret123!";
        String argon2Hash = "$argon2id$v=19$m=65536,t=3,p=1$c29tZXNhbHQ$hashpayloadnotplaintext";
        String userId = insertUser(email, argon2Hash);

        String rawToken = "raw-session-token-" + UUID.randomUUID();
        String tokenHash = sha256hex(rawToken);
        insertSession(userId, tokenHash);

        try (Connection conn = dataSource.getConnection()) {
            String storedPassword = queryString(conn, "SELECT password_hash FROM users WHERE id = ?", userId);
            assertThat(storedPassword)
                    .as("密码必须存储为 Argon2id 哈希格式而非明文")
                    .startsWith("$argon2id$")
                    .isNotEqualTo(plainPassword)
                    .doesNotContain(plainPassword);

            String storedToken = queryString(conn, "SELECT token_hash FROM user_sessions WHERE id = ?",
                    createdSessionIds.get(createdSessionIds.size() - 1));
            assertThat(storedToken)
                    .as("会话令牌必须存储为 SHA-256 十六进制哈希而非明文令牌")
                    .matches("^[0-9a-f]{64}$")
                    .isEqualTo(tokenHash)
                    .isNotEqualTo(rawToken);
        }
    }

    // ---- 辅助方法 ----

    private int userCount(String email) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE email = ?")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int sessionCount(String tokenHash) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM user_sessions WHERE token_hash = ?")) {
            ps.setString(1, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private String queryString(Connection conn, String sql, String param) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    /** 判断某表是否存在指定列（information_schema）。 */
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

    /** 简单的 SHA-256 十六进制（测试用，不引入额外依赖）。 */
    private static String sha256hex(String input) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
