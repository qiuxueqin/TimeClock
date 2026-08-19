package com.timeclock;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * S0-BE-01 工程烟雾测试（TEST-S0-BE-01-01）。
 *
 * <p>验证：无外接依赖（禁用 DataSource/远程 MySQL，避免 S0-BE-01 阶段未就绪的
 * 数据库阻塞应用上下文启动），仅校验 Spring 应用上下文可加载、应用类可解析。
 * S0-DB-01 建立 Flyway/远程 MySQL 基线后，再由对应的集成测试接入真实数据库。
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
})
class TimeClockApplicationTests {

    @Test
    void contextLoads() {
        assertThat(TimeClockApplication.class).isNotNull();
    }
}
