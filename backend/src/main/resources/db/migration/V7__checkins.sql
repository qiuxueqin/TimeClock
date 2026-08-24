-- S4-DB-01：清单自动打卡事实。
-- 迁移不可改写；同一任务同一业务日期仅允许一条记录。
CREATE TABLE checkins (
    id               CHAR(36)     NOT NULL,
    task_id          CHAR(36)     NOT NULL,
    checkin_date     DATE         NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    planned_count    INT          NOT NULL,
    completed_count  INT          NOT NULL,
    makeup_reason    VARCHAR(500) NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_checkins_task_date (task_id, checkin_date),
    KEY idx_checkins_task_status_date (task_id, status, checkin_date),
    CONSTRAINT fk_checkins_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT chk_checkins_status CHECK (status IN ('completed', 'partial', 'missed', 'makeup')),
    CONSTRAINT chk_checkins_planned_count CHECK (planned_count >= 0),
    CONSTRAINT chk_checkins_completed_count CHECK (completed_count >= 0 AND completed_count <= planned_count)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '任务每日打卡事实';
