-- S3 durable confirmation idempotency (V6)
-- Immutable migration: request identity and first response survive process restarts.
CREATE TABLE idempotency_keys (
    id             CHAR(36)     NOT NULL,
    user_id        CHAR(36)     NOT NULL,
    task_id        CHAR(36)     NOT NULL,
    operation      VARCHAR(64)  NOT NULL COMMENT '业务操作范围，例如 xlsx-confirm 或 paste-confirm',
    request_key    VARCHAR(128) NOT NULL,
    request_hash   CHAR(64)     NOT NULL COMMENT '规范化请求 JSON 的 SHA-256',
    response_json  LONGTEXT     NULL COMMENT '首次成功响应 JSON 快照',
    created_at     DATETIME(6)  NOT NULL,
    expires_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_idempotency_user_task_operation_key (user_id, task_id, operation, request_key),
    KEY idx_idempotency_expires_at (expires_at),
    CONSTRAINT fk_idempotency_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_idempotency_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '写请求幂等键及首次响应快照';
