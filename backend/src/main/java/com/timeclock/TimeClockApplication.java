package com.timeclock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 学习打卡系统 V1.0 后端应用入口（模块化单体）。
 *
 * <p>模块边界（backend-tech-stack-V1.0.md §3.1）：
 * auth / user / task / schedule / item / submission / file / importing / job / audit。
 * 包根统一为 com.timeclock.&lt;module&gt;。
 */
@SpringBootApplication
@EnableScheduling
public class TimeClockApplication {

    public static void main(String[] args) {
        SpringApplication.run(TimeClockApplication.class, args);
    }
}
