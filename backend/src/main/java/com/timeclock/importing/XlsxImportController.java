package com.timeclock.importing;

import com.timeclock.auth.BusinessException;
import com.timeclock.auth.SessionAuthenticationFilter;
import com.timeclock.common.RequestContext;
import com.timeclock.importing.dto.XlsxConfirmRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class XlsxImportController {
    private final XlsxImportService service;
    public XlsxImportController(XlsxImportService service) { this.service = service; }

    @PostMapping("/api/v1/tasks/{taskId}/imports/xlsx/preview")
    public ResponseEntity<?> preview(@PathVariable String taskId, @RequestPart("file") MultipartFile file,
                                     Authentication authentication) {
        return ok(service.preview(uid(authentication), taskId, file));
    }

    @PostMapping("/api/v1/tasks/{taskId}/imports/xlsx/confirm")
    public ResponseEntity<?> confirm(@PathVariable String taskId,
                                     @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                     @RequestBody XlsxConfirmRequest request,
                                     Authentication authentication) {
        return ok(service.confirm(uid(authentication), taskId, key, request));
    }

    private ResponseEntity<?> ok(Object value) {
        return ResponseEntity.ok(Map.of("data", value, "requestId", RequestContext.requestId()));
    }
    private String uid(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof SessionAuthenticationFilter.AuthenticatedUser user) return user.id();
        throw new BusinessException("UNAUTHORIZED", "请先登录", 401);
    }
}
