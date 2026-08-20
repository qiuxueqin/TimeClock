package com.timeclock.task;

import com.timeclock.auth.BusinessException;
import com.timeclock.auth.SessionAuthenticationFilter;
import com.timeclock.common.RequestContext;
import com.timeclock.task.dto.TaskCreateRequest;
import com.timeclock.task.dto.TaskUpdateRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) String status,
                                  @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "20") int pageSize,
                                  Authentication authentication) {
        return ResponseEntity.ok(success(taskService.list(userId(authentication), status, page, pageSize)));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<?> get(@PathVariable String taskId, Authentication authentication) {
        return ResponseEntity.ok(success(taskService.get(userId(authentication), taskId)));
    }

    @PatchMapping("/{taskId}")
    public ResponseEntity<?> update(@PathVariable String taskId, @Valid @RequestBody TaskUpdateRequest request,
                                    Authentication authentication) {
        return ResponseEntity.ok(success(taskService.update(userId(authentication), taskId, request)));
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> delete(@PathVariable String taskId, Authentication authentication) {
        taskService.delete(userId(authentication), taskId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody TaskCreateRequest request,
                                    Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(success(taskService.create(userId(authentication), request)));
    }

    @PostMapping("/{taskId}/activate")
    public ResponseEntity<?> activate(@PathVariable String taskId, Authentication authentication) {
        return ResponseEntity.ok(success(taskService.activate(userId(authentication), taskId)));
    }

    private String userId(Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal() instanceof SessionAuthenticationFilter.AuthenticatedUser user) {
            return user.id();
        }
        throw new BusinessException("UNAUTHORIZED", "请先登录", 401);
    }

    private Map<String, Object> success(Object data) {
        return Map.of("data", data, "requestId", RequestContext.requestId());
    }
}
