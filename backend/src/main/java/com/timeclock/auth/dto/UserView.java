package com.timeclock.auth.dto;

/**
 * 当前用户视图（UserView）。
 *
 * <p>对齐 OpenAPI 契约：id / email / timezone。
 */
public record UserView(
        String id,
        String email,
        String timezone
) {
}
