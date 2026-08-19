package com.timeclock.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.timeclock.auth.dto.RegisterRequest;
import com.timeclock.auth.dto.RegisterResponse;
import com.timeclock.common.RequestContext;

/**
 * 认证 REST 控制器：注册（S1-BE-01，REQ-AUTH-01）。
 *
 * <p>路径：POST /api/v1/auth/register。注册成功返回当前用户视图；
 * 失败由 {@code ApiExceptionHandler} 映射为 EnvelopeError。
 *
 * <p>注意：注册写操作需要 CSRF（token 经 GET /auth/csrf 获取，注册前即可获取），
 * S1-BE-04 将启用全局 CSRF 强制；当前步骤先建立业务正确性，CSRF 门禁由 S1-BE-04 验证。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.ok(java.util.Map.of(
                "data", response,
                "requestId", RequestContext.requestId()));
    }
}
