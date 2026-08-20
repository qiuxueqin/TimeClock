package com.timeclock.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.timeclock.auth.dto.LoginRequest;
import com.timeclock.auth.dto.LoginResponse;
import com.timeclock.auth.dto.RegisterRequest;
import com.timeclock.auth.dto.RegisterResponse;
import com.timeclock.auth.dto.UserView;
import com.timeclock.common.RequestContext;

@Service
public class AuthService {

    private static final int RATE_LIMIT_MAX_FAILURES = 5;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(15);
    private static final int PASSWORD_MIN = 8;
    private static final int PASSWORD_MAX = 128;

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final LoginRateLimiter rateLimiter = new LoginRateLimiter(
            RATE_LIMIT_MAX_FAILURES, RATE_LIMIT_WINDOW);
    private final SessionService sessionService;

    public AuthService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder,
                       SessionService sessionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
    }

    public RegisterResponse register(RegisterRequest req) {
        String email = normalizeEmail(req.email());
        String rateKey = rateLimitKey(email);
        if (!rateLimiter.isAllowed(rateKey)) {
            throw new BusinessException("RATE_LIMITED", "操作过于频繁，请稍后再试", 429);
        }
        validatePassword(req.password(), req.confirmPassword());
        String userId = UUID.randomUUID().toString();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        try {
            int rows = jdbcTemplate.update(
                    "INSERT INTO users (id, email, password_hash, timezone, status, created_at, updated_at) "
                            + "VALUES (?, ?, ?, 'Asia/Shanghai', 'active', ?, ?)",
                    userId, email, passwordEncoder.encode(req.password()), now, now);
            if (rows != 1) {
                throw new IllegalStateException("注册写入失败");
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("EMAIL_TAKEN", "该邮箱已被注册", 409);
        }
        rateLimiter.clear(rateKey);
        return new RegisterResponse(new UserView(userId, email, "Asia/Shanghai"));
    }

    public LoginResponse login(LoginRequest req, String userAgent) {
        String email = normalizeEmail(req.email());
        String rateKey = rateLimitKey(email);
        if (!rateLimiter.isAllowed(rateKey)) {
            throw new BusinessException("RATE_LIMITED", "操作过于频繁，请稍后再试", 429);
        }
        String password = req.password();
        if (password == null || password.isEmpty()) {
            rateLimiter.recordFailure(rateKey);
            throw invalidCredentials();
        }

        Optional<UserRecord> user = jdbcTemplate.query(
                "SELECT id, email, password_hash, timezone FROM users "
                        + "WHERE email_normalized = LOWER(TRIM(?)) AND status = 'active'",
                rs -> rs.next()
                        ? Optional.of(new UserRecord(rs.getString("id"), rs.getString("email"),
                                rs.getString("password_hash"), rs.getString("timezone")))
                        : Optional.empty(), email);
        if (user.isEmpty() || !passwordEncoder.matches(password, user.get().passwordHash())) {
            rateLimiter.recordFailure(rateKey);
            throw invalidCredentials();
        }
        rateLimiter.clear(rateKey);
        SessionService.IssuedSession issued = sessionService.issue(user.get().id(), userAgent);
        return new LoginResponse(new UserView(user.get().id(), user.get().email(), user.get().timezone()),
                issued.rawToken());
    }

    private BusinessException invalidCredentials() {
        return new BusinessException("INVALID_CREDENTIALS", "邮箱或密码不正确", 401);
    }

    String normalizeEmail(String raw) {
        if (raw == null) throw new BusinessException("VALIDATION_ERROR", "邮箱不能为空", 422);
        String email = raw.trim().toLowerCase(Locale.ROOT);
        if (email.isEmpty()) throw new BusinessException("VALIDATION_ERROR", "邮箱不能为空", 422);
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new BusinessException("VALIDATION_ERROR", "邮箱格式不正确", 422);
        }
        return email;
    }

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

    private record UserRecord(String id, String email, String passwordHash, String timezone) {
    }
}
