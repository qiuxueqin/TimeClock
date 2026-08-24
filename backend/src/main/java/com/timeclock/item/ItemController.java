package com.timeclock.item;

import com.timeclock.auth.BusinessException;
import com.timeclock.auth.SessionAuthenticationFilter;
import com.timeclock.common.RequestContext;
import com.timeclock.item.dto.*;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class ItemController {
 private final ItemService service; public ItemController(ItemService service){this.service=service;}
 @GetMapping("/api/v1/tasks/{taskId}/items") public ResponseEntity<?> list(@PathVariable String taskId,@RequestParam(required=false)String status,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize,Authentication a){return ok(service.list(uid(a),taskId,status,page,pageSize));}
 @PostMapping("/api/v1/tasks/{taskId}/items") public ResponseEntity<?> create(@PathVariable String taskId,@Valid @RequestBody ItemCreateRequest q,Authentication a){return ResponseEntity.status(201).body(envelope(service.create(uid(a),taskId,q)));}
 @PostMapping("/api/v1/tasks/{taskId}/items/paste-preview") public ResponseEntity<?> preview(@PathVariable String taskId,@Valid @RequestBody PastePreviewRequest q,Authentication a){return ok(service.preview(uid(a),taskId,q));}
 @PostMapping("/api/v1/tasks/{taskId}/items/paste-confirm") public ResponseEntity<?> confirm(@PathVariable String taskId,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody PasteConfirmRequest q,Authentication a){return ok(service.confirm(uid(a),taskId,key,q));}
 @GetMapping("/api/v1/items/{itemId}") public ResponseEntity<?> get(@PathVariable String itemId,Authentication a){return ok(service.get(uid(a),itemId));}
 @PatchMapping("/api/v1/items/{itemId}") public ResponseEntity<?> update(@PathVariable String itemId,@Valid @RequestBody ItemUpdateRequest q,Authentication a){return ok(service.update(uid(a),itemId,q));}
 @GetMapping("/api/v1/items/{itemId}/submission") public ResponseEntity<?> submission(@PathVariable String itemId,Authentication a){return ok(service.submission(uid(a),itemId));}
 @PutMapping("/api/v1/items/{itemId}/submission") public ResponseEntity<?> save(@PathVariable String itemId,@Valid @RequestBody SolutionRequest q,Authentication a){return ok(service.saveSolution(uid(a),itemId,q));}
 @PostMapping("/api/v1/items/{itemId}/complete") public ResponseEntity<?> complete(@PathVariable String itemId,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody SolutionRequest q,Authentication a){return ok(service.complete(uid(a),itemId,key,q));}
 @PostMapping("/api/v1/items/{itemId}/reopen") public ResponseEntity<?> reopen(@PathVariable String itemId,@RequestHeader("Idempotency-Key") String key,Authentication a){return ok(service.reopen(uid(a),itemId,key));}
 @GetMapping("/api/v1/tasks/{taskId}/today-items") public ResponseEntity<?> today(@PathVariable String taskId,Authentication a){return ok(service.today(uid(a),taskId));}
 private ResponseEntity<?> ok(Object x){return ResponseEntity.ok(envelope(x));} private Map<String,Object> envelope(Object x){return Map.of("data",x,"requestId",RequestContext.requestId());} private String uid(Authentication a){if(a!=null&&a.getPrincipal() instanceof SessionAuthenticationFilter.AuthenticatedUser u)return u.id();throw new BusinessException("UNAUTHORIZED","请先登录",401);}
}
