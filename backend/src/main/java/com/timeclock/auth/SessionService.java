package com.timeclock.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SessionService {
    public static final String COOKIE_NAME = "SESSION_ID";
    public static final Duration SESSION_LIFETIME = Duration.ofDays(30);
    public static final Duration RENEW_AFTER = Duration.ofDays(15);

    private final JdbcTemplate jdbcTemplate;
    private final SecureRandom secureRandom;
    private final Clock clock;

    @Autowired
    public SessionService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new SecureRandom(), Clock.systemUTC());
    }

    SessionService(JdbcTemplate jdbcTemplate, SecureRandom secureRandom, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    public IssuedSession issue(String userId, String userAgent) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String raw = HexFormat.of().formatHex(bytes);
        Instant now = now();
        jdbcTemplate.update(
                "INSERT INTO user_sessions (id, user_id, token_hash, expires_at, last_accessed_at, device_summary, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), userId, hash(raw),
                sqlTime(now.plus(SESSION_LIFETIME)), sqlTime(now), summarize(userAgent), sqlTime(now));
        return new IssuedSession(raw, userId, now.plus(SESSION_LIFETIME));
    }

    public SessionRecord findValid(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return null;
        Instant now = now();
        return jdbcTemplate.query(
                "SELECT s.id, s.user_id, s.token_hash, s.expires_at, s.last_accessed_at, "
                        + "u.email, u.timezone FROM user_sessions s JOIN users u ON u.id=s.user_id "
                        + "WHERE s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>? AND u.status='active'",
                rs -> rs.next() ? new SessionRecord(rs.getString("id"), rs.getString("user_id"),
                        rs.getString("token_hash"), rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("last_accessed_at").toInstant(), rs.getString("email"), rs.getString("timezone")) : null,
                hash(rawToken), sqlTime(now));
    }

    public boolean renewIfStale(SessionRecord session) {
        Instant now = now();
        if (session.lastAccessedAt().plus(RENEW_AFTER).isAfter(now)) return false;
        return jdbcTemplate.update(
                "UPDATE user_sessions SET last_accessed_at=?, expires_at=? "
                        + "WHERE id=? AND revoked_at IS NULL AND expires_at>?",
                sqlTime(now), sqlTime(now.plus(SESSION_LIFETIME)), session.id(), sqlTime(now)) == 1;
    }

    public void revoke(String tokenHash) {
        if (tokenHash == null) return;
        jdbcTemplate.update("UPDATE user_sessions SET revoked_at=? WHERE token_hash=? AND revoked_at IS NULL",
                sqlTime(now()), tokenHash);
    }

    public static String hash(String rawToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
    private static java.sql.Timestamp sqlTime(Instant instant) { return java.sql.Timestamp.from(instant); }
    private static String summarize(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return null;
        return userAgent.length() <= 200 ? userAgent : userAgent.substring(0, 200);
    }

    public record IssuedSession(String rawToken, String userId, Instant expiresAt) {}
    public record SessionRecord(String id, String userId, String tokenHash, Instant expiresAt,
                                Instant lastAccessedAt, String email, String timezone) {}
}
