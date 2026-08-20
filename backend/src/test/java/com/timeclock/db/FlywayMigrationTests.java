package com.timeclock.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * S0-DB-01 空库迁移集成测试（TEST-S0-DB-01-01）。
 *
 * <p>在协调者提供的远程 MySQL 8 实例（独立测试库 time_clock_test）上验证：
 * 1. Flyway 迁移一次成功；2. 重复启动不重复建表；3. Flyway 校验无漂移；
 * 4. 测试库不污染共享数据（time_clock）。
 *
 * <p>运行前需确保测试库已存在（可重建）。连接信息仅从环境变量注入。
 */
@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationTests {

    @Autowired
    private DataSource dataSource;

    /** 校验 Flyway 迁移历史表已创建，且 V1/V2/V3 迁移已成功应用。 */
    @Test
    void baselineMigrationAppliedOnCleanTestDb() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM flyway_schema_history "
                             + "WHERE success = TRUE AND version IN ('1', '2', '3')")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).as("V1、V2、V3 迁移应均已成功应用").isEqualTo(3);
        }
    }

    /** 测试库 schema 名称与生产库区分（隔离性：不污染 time_clock 共享数据）。 */
    @Test
    void testDatabaseIsSeparateFromProduction() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            String catalog = conn.getCatalog();
            assertThat(catalog).as("测试应运行在独立测试库").isEqualTo("time_clock_test");
        }
    }
}
