package com.timeclock.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 最小安全配置（S1-BE-01 阶段）。
 *
 * <p>当前仅允许注册接口通过、禁用默认的 HTTP 基础认证随机密码与 CSRF 校验，
 * 使 S1-BE-01 的注册业务正确性可被 API 测试直接验证。
 *
 * <p>注意：这是临时最小配置，将在后续步骤替换/强化：
 * - S1-BE-02 建立数据库 Session 与 Cookie 签发；
 * - S1-BE-04 启用全局 CSRF 强制（所有写请求必须携带 Token）。
 *
 * <p>会话相关仍交由 Spring Security 默认的 Session 机制；当前不限制端点归属。
 */
@Configuration
@EnableWebSecurity
public class AuthSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // S1-BE-01：注册接口暂时放行；S1-BE-04 将改为"会话写请求必须 CSRF"
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/register", "/actuator/health").permitAll()
                        .anyRequest().permitAll())
                // CSRF 强制推迟到 S1-BE-04（当前步骤先验证注册业务正确性）
                .csrf(csrf -> csrf.disable())
                // 禁用默认的 HTTP Basic 与表单登录随机密码（开发最小化）
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));
        return http.build();
    }
}
