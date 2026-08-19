package com.timeclock.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timeclock.auth.dto.RegisterRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * S1-BE-01 邮箱注册 API 测试（TEST-S1-BE-01-01）。
 *
 * <p>在远程 MySQL 8 测试库上验证：
 * 1. 有效邮箱注册成功，数据库中仅存 Argon2id 哈希（无明文）；
 * 2. 大小写/首尾空白变体邮箱注册被稳定冲突（409，不建用户）；
 * 3. 弱密码、不一致确认密码返回字段错误且不建用户；
 * 4. 重复邮箱返回稳定冲突。
 *
 * <p>注册接口为 CSRF 写接口；S1-BE-04 尚未启用全局 CSRF 强制，故此处直接调用可进入业务校验。
 * 为隔离应用默认的安全自动配置（避免生成随机密码影响测试），测试类不校验默认 HTTP 基础认证。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthRegisterApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 已注册邮箱，供清理。 */
    private final List<String> createdEmails = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (String email : createdEmails) {
            jdbcTemplate.update("DELETE FROM users WHERE email = ?", email);
        }
        createdEmails.clear();
    }

    // ---- 1. 有效注册成功且仅存 Argon2id 哈希 ----

    @Test
    void validRegistrationSucceedsAndStoresArgon2idHash() throws Exception {
        String email = "reg-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String password = "CorrectHorse1!";
        createdEmails.add(email);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterRequest(email, password, password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(jsonPath("$.data.user.id").isNotEmpty())
                .andExpect(jsonPath("$.data.user.timezone").value("Asia/Shanghai"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .as("响应不得包含明文密码")
                .doesNotContain(password);

        // 数据库校验：仅存 Argon2id 哈希，非明文
        String stored = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE email = ?", String.class, email);
        assertThat(stored)
                .as("密码必须存储为 Argon2id 哈希")
                .startsWith("$argon2id$")
                .isNotEqualTo(password);

        // 校验可回放：matches 应返回 true（集成验证编码器与存储格式一致）
        boolean matches = queryPasswordMatches(email, password);
        assertThat(matches).as("存储的 Argon2id 哈希应能校验正确密码").isTrue();
    }

    // ---- 2. 大小写变体邮箱 → 稳定冲突，不建用户 ----

    @Test
    void emailCaseVariantReturnsConflictAndDoesNotCreateUser() throws Exception {
        String base = "case-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        createdEmails.add(base);
        registerOk(base, "Passw0rd!X");
        long countBefore = userCount(base);

        // 大写变体注册 → 409，稳定错误码 EMAIL_TAKEN
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterRequest(base.toUpperCase(), "OtherPass1!", "OtherPass1!"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_TAKEN"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        assertThat(userCount(base)).as("大小写变体注册不应新建用户").isEqualTo(countBefore);
    }

    // ---- 3. 首尾空白变体邮箱 → 稳定冲突（规范化兜底），不建用户 ----

    @Test
    void emailWhitespaceVariantReturnsConflict() throws Exception {
        String base = "ws-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        createdEmails.add(base);
        registerOk(base, "Passw0rd!X");
        long countBefore = userCount(base);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterRequest("  " + base + "  ", "OtherPass1!", "OtherPass1!"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_TAKEN"));

        assertThat(userCount(base)).as("空白变体注册不应新建用户").isEqualTo(countBefore);
    }

    // ---- 4. 弱密码 → 422，不建用户 ----

    @Test
    void weakPasswordReturnsValidationError() throws Exception {
        String email = "weak-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterRequest(email, "short", "short"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("WEAK_PASSWORD"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        assertThat(userCount(email)).as("弱密码注册不应建用户").isZero();
    }

    // ---- 5. 不一致确认密码 → 422，不建用户 ----

    @Test
    void mismatchedConfirmPasswordReturnsValidationError() throws Exception {
        String email = "mismatch-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterRequest(email, "CorrectHorse1!", "WrongHorse1!"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_MISMATCH"));

        assertThat(userCount(email)).as("确认密码不一致注册不应建用户").isZero();
    }

    // ---- 6. 非法邮箱格式 → 422 ----

    @Test
    void invalidEmailFormatReturnsValidationError() throws Exception {
        String email = "not-an-email";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterRequest(email, "CorrectHorse1!", "CorrectHorse1!"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        assertThat(userCount(email)).as("非法邮箱注册不应建用户").isZero();
    }

    // ---- 辅助方法 ----

    private void registerOk(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterRequest(email, password, password))))
                .andExpect(status().isOk());
    }

    private long userCount(String email) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ?", Long.class, email);
        return count == null ? 0 : count;
    }

    private boolean queryPasswordMatches(String email, String password) {
        String stored = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE email = ?", String.class, email);
        if (stored == null) {
            return false;
        }
        // 用独立编码器实例校验，模拟登录校验路径
        Argon2idPasswordEncoder encoder = new Argon2idPasswordEncoder();
        return encoder.matches(password, stored);
    }

    private String json(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }
}
