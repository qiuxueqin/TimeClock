package com.timeclock;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * S0-BE-01 工程烟雾测试（TEST-S0-BE-01-01）。
 *
 * <p>验证 Spring 应用上下文可完整加载、应用类可解析。
 * S1-BE-01 起业务 Bean（AuthService 等）依赖 JdbcTemplate，故本测试连接
 * application-test.yml 的独立测试库（远程 MySQL 8），由 Flyway 自动迁移并启动。
 */
@SpringBootTest
@ActiveProfiles("test")
class TimeClockApplicationTests {

    @Test
    void contextLoads() {
        assertThat(TimeClockApplication.class).isNotNull();
    }
}
