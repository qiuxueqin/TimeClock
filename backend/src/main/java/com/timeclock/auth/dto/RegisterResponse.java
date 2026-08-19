package com.timeclock.auth.dto;

/**
 * 注册成功响应（注册接口返回 UserView，契约见 S1-BE-01 / OpenAPI /auth/register）。
 *
 * <p>成功响应体结构：{ "data": { "user": UserView }, "requestId": "…" }。
 */
public record RegisterResponse(
        UserView user
) {
}
