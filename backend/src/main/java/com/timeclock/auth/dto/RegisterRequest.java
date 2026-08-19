package com.timeclock.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 注册请求 DTO（RegisterRequest）。
 *
 * <p>对齐 OpenAPI 契约：email / password / confirmPassword 均必填。
 * email 为规范化邮箱（唯一，DEC-01）；password 长度 8-128。
 */
public record RegisterRequest(
        String email,
        String password,
        @JsonProperty("confirmPassword") String confirmPassword
) {
}
