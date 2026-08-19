package com.timeclock.auth;

/**
 * 业务异常：用户可理解的稳定错误。
 *
 * <p>code 为前端据此分类的稳定错误码（不解析 message 文本，见 OpenAPI EnvelopeError）；
 * message 为用户可读信息。此类异常由 {@code ApiExceptionHandler} 统一映射为
 * {@code { "error": { "code", "message" }, "requestId" }} 响应体。
 */
public class BusinessException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public BusinessException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
