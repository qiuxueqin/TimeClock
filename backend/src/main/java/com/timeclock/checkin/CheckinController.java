package com.timeclock.checkin;

import com.timeclock.auth.BusinessException;
import com.timeclock.auth.SessionAuthenticationFilter;
import com.timeclock.common.RequestContext;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** S6 打卡详情、月历、统计与补打入口（Session Cookie 鉴权；写请求经全局 CSRF）。 */
@RestController
public class CheckinController {

    private final MakeupService service;
    private final CalendarService calendar;
    private final TaskStatsService stats;

    public CheckinController(MakeupService service, CalendarService calendar, TaskStatsService stats) {
        this.service = service;
        this.calendar = calendar;
        this.stats = stats;
    }

    @GetMapping("/api/v1/tasks/{taskId}/checkins/{date}")
    public ResponseEntity<?> detail(@PathVariable String taskId,
                                    @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                    Authentication authentication) {
        return ResponseEntity.ok(Map.of("data",
                service.detail(uid(authentication), taskId, date), "requestId", RequestContext.requestId()));
    }

    @GetMapping("/api/v1/calendar")
    public ResponseEntity<?> calendar(@RequestParam("month") String month,
                                      @RequestParam(value = "taskId", required = false) String taskId,
                                      @RequestParam(value = "filter", required = false) String filter,
                                      Authentication authentication) {
        return ResponseEntity.ok(Map.of("data",
                Map.of("month", month, "days", calendar.month(uid(authentication), month, taskId, filter)),
                "requestId", RequestContext.requestId()));
    }

    @GetMapping("/api/v1/tasks/{taskId}/stats")
    public ResponseEntity<?> taskStats(@PathVariable String taskId, Authentication authentication) {
        return ResponseEntity.ok(Map.of("data",
                stats.stats(uid(authentication), taskId), "requestId", RequestContext.requestId()));
    }

    @PostMapping("/api/v1/tasks/{taskId}/checkins/{date}/makeup")
    public ResponseEntity<?> makeup(@PathVariable String taskId,
                                    @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                    @RequestHeader("Idempotency-Key") String key,
                                    @Valid @RequestBody MakeupService.MakeupRequest request,
                                    Authentication authentication) {
        return ResponseEntity.ok(Map.of("data",
                service.makeup(uid(authentication), taskId, date, key, request),
                "requestId", RequestContext.requestId()));
    }

    private String uid(Authentication a) {
        if (a != null && a.getPrincipal() instanceof SessionAuthenticationFilter.AuthenticatedUser u) return u.id();
        throw new BusinessException("UNAUTHORIZED", "请先登录", 401);
    }
}
