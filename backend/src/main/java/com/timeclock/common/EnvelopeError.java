package com.timeclock.common;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 统一错误响应体（EnvelopeError）。
 *
 * <p>结构：{ "error": { "code": "…", "message": "…" }, "requestId": "…" }。
 * code 为稳定错误码，前端据此分类（不解析 message 文本）；requestId 贯穿日志用于故障定位。
 */
public record EnvelopeError(
        ErrorBody error,
        @JsonProperty("requestId") String requestId
) {
    public static EnvelopeError of(String code, String message, String requestId) {
        return new EnvelopeError(new ErrorBody(code, message), requestId);
    }

    public record ErrorBody(String code, String message) {
    }
}
