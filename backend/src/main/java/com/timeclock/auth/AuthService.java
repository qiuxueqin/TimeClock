package com.timeclock.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.timeclock.auth.dto.RegisterRequest;
import com.timeclock.auth.dto.RegisterResponse;
import com.timeclock.auth.dto.UserView;
import com.timeclock.common.RequestContext;

/**
 * 认证服务：邮箱注册（S1-BE-01，REQ-AUTH-01/02，DEC-01）。
 *
 * <p>注册流程：
 * 1. 规范化邮箱（小写 + 去首尾空白），与数据库生成列口径一致，使大小写/空白变体唯一。
 * 2. 校验密码与确认密码一致、密码强度（长度 8-128）。
 * 3. 用 {@link Argon2idPasswordEncoder} 对密码做 Argon2id 哈希，禁止明文。
 * 4. 统一处理重复邮箱 → 稳定冲突（409），不暴露内部异常或已有邮箱存在性。
 * 5. 单实例限流（默认 5 次失败 / 15 分钟 / IP+邮箱，§3.4）。
 *
 * <p>日志不得记录密码或完整请求体（完成标准）。
 */
@Service
public class AuthService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(AuthService.class);

    private static final int RATE_LIMIT_MAX_FAILURES = 5;
    private static final java.time.Duration RATE_LIMIT_WINDOW = java.time.Duration.ofMinutes(15);
    private static final int PASSWORD_MIN = 8;
    private static final int PASSWORD_MAX = 128;

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final LoginRateLimiter registerRateLimiter = new LoginRateLimiter(
            RATE_LIMIT_MAX_FAILURES, RATE_LIMIT_WINDOW);

    public AuthService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 注册一个新用户。成功返回用户视图；重复邮箱抛 409；限流抛 429；参数非法抛 422。
     */
    public RegisterResponse register(RegisterRequest req) {
        String email = normalizeEmail(req.email());
        String rateKey = rateLimitKey(email);
        if (!registerRateLimiter.isAllowed(rateKey)) {
            throw new BusinessException("RATE_LIMITED", "操作过于频繁，请稍后再试", 429);
        }

        validatePassword(req.password(), req.confirmPassword());

        String passwordHash = passwordEncoder.encode(req.password());
        String userId = UUID.randomUUID().toString();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        try {
            int rows = jdbcTemplate.update(
                    "INSERT INTO users (id, email, password_hash, timezone, overdue_reminder_visible, "
                            + "status, version, created_at, updated_at) "
                            + "VALUES (?, ?, ?, 'Asia/Shanghai', 1, 'active', 0, ?, ?)",
                    userId, email, passwordHash, now, now);
            if (rows != 1) {
                throw new IllegalStateException("注册写入失败 rows=" + rows);
            }
        } catch (DuplicateKeyException ex) {
            // 邮箱唯一冲突：统一稳定错误，不暴露内部异常/邮箱存在性（DEC-01）
            throw new BusinessException("EMAIL_TAKEN", "该邮箱已被注册", 409);
        }

        log.info("注册成功 req={} email={}", RequestContext.requestId(), email);
        return new RegisterResponse(toUserView(userId, email));
    }

    /** 规范化邮箱：小写 + 去首尾空白；非法格式抛 422。 */
    private String normalizeEmail(String raw) {
        if (raw == null) {
            throw new BusinessException("VALIDATION_ERROR", "邮箱不能为空", 422);
        }
        String email = raw.trim().toLowerCase(Locale.ROOT);
        if (email.isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "邮箱不能为空", 422);
        }
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new BusinessException("VALIDATION_ERROR", "邮箱格式不正确", 422);
        }
        return email;
    }

    /** 校验密码与确认密码一致性及长度。 */
    private void validatePassword(String password, String confirmPassword) {
        if (password == null || confirmPassword == null) {
            throw new BusinessException("VALIDATION_ERROR", "密码不能为空", 422);
        }
        if (!password.equals(confirmPassword)) {
            throw new BusinessException("PASSWORD_MISMATCH", "两次输入的密码不一致", 422);
        }
        if (password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX) {
            throw new BusinessException("WEAK_PASSWORD", "密码长度需为 8-128 个字符", 422);
        }
    }

    private String rateLimitKey(String email) {
        return Optional.ofNullable(RequestContext.clientIp()).orElse("unknown") + "|" + email;
    }

    private UserView toUserView(String userId, String email) {
        return new UserView(userId, email, "Asia/Shanghai", true);
    }
}
