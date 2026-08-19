package com.timeclock.common;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 请求上下文过滤器：为每个请求生成 requestId（贯穿日志），并提取客户端 IP。
 *
 * <p>请求完成后清理 ThreadLocal 与 MDC，避免线程复用串扰。客户端 IP 取 X-Forwarded-For
 * 首个地址；反向代理单层部署下用于认证限流（§3.4 登录/注册限流按 IP+邮箱计数）。
 */
@Component("timeclockRequestContextFilter")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestContextFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        String clientIp = resolveClientIp(request);
        MDC.put("requestId", requestId);
        RequestContext.set(requestId, clientIp);
        try {
            filterChain.doFilter(request, response);
        } finally {
            RequestContext.clear();
            MDC.remove("requestId");
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr == null ? "unknown" : remoteAddr;
    }
}
