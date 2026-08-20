package com.timeclock.auth;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.timeclock.auth.dto.CsrfTokenResponse;
import com.timeclock.auth.dto.LoginRequest;
import com.timeclock.auth.dto.LoginResponse;
import com.timeclock.auth.dto.RegisterRequest;
import com.timeclock.auth.dto.RegisterResponse;
import com.timeclock.auth.dto.UserView;
import com.timeclock.common.RequestContext;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final SessionService sessionService;

    public AuthController(AuthService authService, SessionService sessionService) {
        this.authService = authService;
        this.sessionService = sessionService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest,
                                   HttpServletResponse httpResponse) {
        LoginResponse response = authService.login(request, httpRequest.getHeader("User-Agent"));
        httpResponse.addHeader("Set-Cookie", AuthCookie.session(response.rawToken(),
                java.time.Instant.now().plus(SessionService.SESSION_LIFETIME)).toString());
        return ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (!(authentication != null && authentication.getPrincipal() instanceof SessionAuthenticationFilter.AuthenticatedUser user)) {
            throw new BusinessException("UNAUTHORIZED", "请先登录", 401);
        }
        return ok(new UserView(user.id(), user.email(), user.timezone()));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        String raw = SessionAuthenticationFilter.cookie(request, SessionService.COOKIE_NAME);
        if (raw != null) sessionService.revoke(SessionService.hash(raw));
        response.addHeader("Set-Cookie", AuthCookie.clear().toString());
        return ok(Map.of("loggedOut", true));
    }

    @GetMapping("/csrf")
    public ResponseEntity<?> csrf(CsrfToken token) {
        return ok(new CsrfTokenResponse(token.getToken()));
    }

    private ResponseEntity<?> ok(Object data) {
        return ResponseEntity.ok(Map.of("data", data, "requestId", RequestContext.requestId()));
    }
}
