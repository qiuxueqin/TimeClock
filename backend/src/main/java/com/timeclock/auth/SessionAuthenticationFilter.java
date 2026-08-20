package com.timeclock.auth;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {
    private final SessionService sessionService;

    public SessionAuthenticationFilter(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String raw = cookie(request, SessionService.COOKIE_NAME);
        SessionService.SessionRecord session = sessionService.findValid(raw);
        if (session != null) {
            var authentication = new UsernamePasswordAuthenticationToken(
                    new AuthenticatedUser(session.userId(), session.email(), session.timezone()), null, java.util.List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            request.setAttribute("timeclock.session", session);
            if (sessionService.renewIfStale(session)) {
                response.addHeader("Set-Cookie", AuthCookie.session(raw, java.time.Instant.now().plus(SessionService.SESSION_LIFETIME)).toString());
            }
        }
        filterChain.doFilter(request, response);
    }

    static String cookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) if (name.equals(cookie.getName())) return cookie.getValue();
        return null;
    }

    public record AuthenticatedUser(String id, String email, String timezone) {}
}
