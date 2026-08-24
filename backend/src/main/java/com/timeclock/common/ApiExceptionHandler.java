package com.timeclock.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import com.timeclock.auth.BusinessException;

/**
 * 全局异常处理：将业务与校验异常统一映射为 EnvelopeError 响应体。
 *
 * <p>映射规则（实施计划 §3.2）：字段校验失败 → 422；重复邮箱（业务冲突）→ 409；
 * 限流 → 429；其余未预期异常 → 500（生产不暴露详细异常，仅返回稳定错误码）。
 *
 * <p>日志不得记录密码或完整请求体（S1-BE-01 完成标准）；此处仅记录 requestId 与稳定错误码。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** 业务异常：按 BusinessException 携带的状态码与错误码返回。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<EnvelopeError> handleBusiness(BusinessException ex) {
        String requestId = RequestContext.requestId();
        log.info("业务异常 req={} code={}", requestId, ex.getCode());
        return ResponseEntity.status(ex.getHttpStatus())
                .body(EnvelopeError.of(ex.getCode(), ex.getMessage(), requestId));
    }

    /** 字段校验失败（@Valid 触发）。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<EnvelopeError> handleValidation(MethodArgumentNotValidException ex) {
        String requestId = RequestContext.requestId();
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("请求参数校验失败");
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .body(EnvelopeError.of("VALIDATION_ERROR", message, requestId));
    }

    /** JSON 类型、日期或未知枚举格式错误。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<EnvelopeError> handleUnreadable(HttpMessageNotReadableException ex) {
        String requestId = RequestContext.requestId();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .body(EnvelopeError.of("VALIDATION_ERROR", "请求参数格式不正确", requestId));
    }

    /** 缺少必需请求头（如 Idempotency-Key）。 */
    @ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class)
    public ResponseEntity<EnvelopeError> handleMissingHeader(
            org.springframework.web.bind.MissingRequestHeaderException ex) {
        String requestId = RequestContext.requestId();
        log.info("缺少请求头 req={} name={}", requestId, ex.getHeaderName());
        return ResponseEntity.badRequest()
                .body(EnvelopeError.of("VALIDATION_ERROR", "缺少必要请求头: " + ex.getHeaderName(), requestId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<EnvelopeError> handleUnexpected(Exception ex) {
        String requestId = RequestContext.requestId();
        log.error("未预期异常 req={} type={} msg={}",
                requestId, ex.getClass().getSimpleName(), safeMessage(ex));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .body(EnvelopeError.of("INTERNAL_ERROR", "服务器内部错误", requestId));
    }

    private String safeMessage(Exception ex) {
        String m = ex.getMessage();
        return m == null ? "" : m;
    }
}
