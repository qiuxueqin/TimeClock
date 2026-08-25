package com.timeclock.checkin;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * S6-BE-03 调度装配：默认使用系统 UTC 时钟；测试可注入固定时钟验证任务时区边界。
 */
@Service
public class CheckinSettlementScheduler {
    private final CheckinSettlementService service;

    @Autowired
    public CheckinSettlementScheduler(CheckinSettlementService service) {
        this.service = service;
    }

    public CheckinSettlementService service() {
        return service;
    }

    static Clock defaultClock() {
        return Clock.systemUTC();
    }
}
