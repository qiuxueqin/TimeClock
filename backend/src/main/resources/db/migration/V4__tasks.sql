-- S2-DB-01 建立清单任务模型
-- 任务仅支持 checklist、daily；状态仅 draft/active。
-- 迁移不可改写；后续修正只能追加新迁移。
-- 任务删除由后续业务层以事务方式物理删除并清理关联资源。

CREATE TABLE tasks (
    id                  CHAR(36)     NOT NULL COMMENT '任务 ID（UUID）',
    user_id             CHAR(36)     NOT NULL COMMENT '归属用户 ID',
    name                VARCHAR(50)  NOT NULL COMMENT '任务名称（1-50 个字符）',
    description         VARCHAR(500) NULL COMMENT '任务描述（最多 500 个字符）',
    start_date          DATE         NOT NULL COMMENT '任务开始自然日，不按 UTC 重解释',
    end_date            DATE         NULL COMMENT '任务结束自然日，NULL 表示长期',
    task_type           VARCHAR(20)  NOT NULL DEFAULT 'checklist' COMMENT 'V1.0 固定 checklist',
    schedule_type       VARCHAR(20)  NOT NULL DEFAULT 'daily' COMMENT 'V1.0 固定 daily',
    timezone            VARCHAR(64)  NOT NULL COMMENT '任务 IANA 时区',
    daily_target_count  INT          NOT NULL COMMENT '每日目标，必须 >= 1',
    status              VARCHAR(20)  NOT NULL DEFAULT 'draft' COMMENT 'draft 或 active',
    created_at          DATETIME(6)  NOT NULL COMMENT '创建时间',
    updated_at          DATETIME(6)  NOT NULL COMMENT '最后更新时间',
    PRIMARY KEY (id),
    CONSTRAINT fk_tasks_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_tasks_type CHECK (task_type = 'checklist'),
    CONSTRAINT chk_tasks_schedule CHECK (schedule_type = 'daily'),
    CONSTRAINT chk_tasks_status CHECK (status IN ('draft', 'active')),
    CONSTRAINT chk_tasks_daily_target CHECK (daily_target_count >= 1),
    CONSTRAINT chk_tasks_date_range CHECK (end_date IS NULL OR end_date >= start_date),
    UNIQUE KEY uk_tasks_user_name (user_id, name),
    KEY idx_tasks_user_status (user_id, status),
    KEY idx_tasks_user_created_id (user_id, created_at DESC, id DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '清单任务';
