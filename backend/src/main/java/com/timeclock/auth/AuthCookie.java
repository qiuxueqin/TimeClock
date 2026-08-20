package com.timeclock.auth;

import java.time.Duration;
import java.time.Instant;

import org.springframework.http.ResponseCookie;

public final class AuthCookie {
    private AuthCookie() {}

    public static ResponseCookie session(String token, Instant expiresAt) {
        long maxAge = Math.max(0, Duration.between(Instant.now(), expiresAt).getSeconds());
        return ResponseCookie.from(SessionService.COOKIE_NAME, token)
                .httpOnly(true).secure(true).sameSite("Lax").path("/").maxAge(maxAge).build();
    }

    public static ResponseCookie clear() {
        return ResponseCookie.from(SessionService.COOKIE_NAME, "")
                .httpOnly(true).secure(true).sameSite("Lax").path("/").maxAge(0).build();
    }
}
