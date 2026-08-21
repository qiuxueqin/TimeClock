-- S3-DB-01 建立学习条目模型
-- 迁移不可改写；后续修正只能追加新迁移。
-- 条目随所属任务物理删除，避免遗留无主学习数据。

CREATE TABLE learning_items (
    id             CHAR(36)      NOT NULL COMMENT '学习条目 ID（UUID）',
    task_id        CHAR(36)      NOT NULL COMMENT '所属任务 ID',
    title          VARCHAR(255)  NOT NULL COMMENT '题目标题',
    content        TEXT          NULL COMMENT '题目正文/链接/说明',
    analysis       TEXT          NULL COMMENT '题库解析，仅供查看',
    external_url   VARCHAR(2048) NULL COMMENT '题目外链',
    sort_order     INT           NOT NULL COMMENT '任务内学习顺序，从 1 开始',
    status         VARCHAR(20)   NOT NULL DEFAULT 'pending' COMMENT 'pending 或 completed',
    solution_text  TEXT          NULL COMMENT '用户文字题解',
    completed_at   DATETIME(6)   NULL COMMENT '完成时间',
    created_at     DATETIME(6)   NOT NULL COMMENT '创建时间',
    updated_at     DATETIME(6)   NOT NULL COMMENT '最后更新时间',
    PRIMARY KEY (id),
    CONSTRAINT fk_learning_items_task
        FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT chk_learning_items_title
        CHECK (CHAR_LENGTH(TRIM(title)) >= 1),
    CONSTRAINT chk_learning_items_sort_order
        CHECK (sort_order >= 1),
    CONSTRAINT chk_learning_items_status
        CHECK (status IN ('pending', 'completed')),
    UNIQUE KEY uk_learning_items_task_sort_order (task_id, sort_order),
    KEY idx_learning_items_task_status_order (task_id, status, sort_order),
    KEY idx_learning_items_task_title (task_id, title)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '学习条目';
