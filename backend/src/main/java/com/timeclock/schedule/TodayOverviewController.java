package com.timeclock.schedule;

import com.timeclock.auth.BusinessException;
import com.timeclock.auth.SessionAuthenticationFilter;
import com.timeclock.common.RequestContext;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** S5 今日总览入口：GET /api/v1/dashboard/today（只读，Session Cookie 鉴权，无 CSRF 要求）。 */
@RestController
public class TodayOverviewController {
    private final TodayOverviewService service;

    public TodayOverviewController(TodayOverviewService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/dashboard/today")
    public ResponseEntity<?> today(Authentication authentication) {
        return ResponseEntity.ok(Map.of("data", service.today(uid(authentication)), "requestId", RequestContext.requestId()));
    }

    private String uid(Authentication a) {
        if (a != null && a.getPrincipal() instanceof SessionAuthenticationFilter.AuthenticatedUser u) return u.id();
        throw new BusinessException("UNAUTHORIZED", "请先登录", 401);
    }
}
