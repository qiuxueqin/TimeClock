package com.timeclock.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 认证 Bean 装配。
 *
 * <p>提供全局 {@link PasswordEncoder}（Argon2id，DEC-01）。
 * Spring Security 的完整过滤链与会话/CSRF 门禁在 S1-BE-02 / S1-BE-04 建立。
 */
@Configuration
public class AuthBeansConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Argon2idPasswordEncoder();
    }
}
