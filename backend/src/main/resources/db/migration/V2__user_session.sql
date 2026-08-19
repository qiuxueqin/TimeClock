-- S1-DB-01 建立用户与会话模型
-- 对应步骤：S1-DB-01（编号由数据库 Agent 登记，记录对应步骤编号，见实施计划 §0.2）
-- 规则：UTF-8 utf8mb4；字段 snake_case；主键 UUID 用 CHAR(36)；审计字段 created_at / updated_at。
-- 迁移不可改写；后续只允许追加修正迁移。
-- 连接信息仅通过环境变量注入；本文件不含任何凭据。

-- 用户表：邮箱唯一（大小写与空白规范化后仍唯一）；密码只存 Argon2id 哈希，禁止明文；
-- 保存用户默认时区、站内逾期提示偏好与账号状态（DEC-01、REQ-USER-01、backend §5）。
CREATE TABLE users (
    id                         CHAR(36)     NOT NULL COMMENT '用户 ID（UUID）',
    email                      VARCHAR(320) NOT NULL COMMENT '邮箱（应用层规范化：小写 + 去首尾空白）',
    password_hash              VARCHAR(255) NOT NULL COMMENT 'Argon2id 密码哈希，禁止明文密码',
    timezone                   VARCHAR(64)  NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '默认 IANA 时区，仅作为新任务默认值（DEC-14）',
    overdue_reminder_visible   TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '站内逾期提示显示偏好（REQ-USER-01），仅控制界面展示',
    status                     VARCHAR(20)  NOT NULL DEFAULT 'active' COMMENT '账号状态',
    version                    BIGINT       NOT NULL DEFAULT 0 COMMENT '乐观锁版本（S2-BE-07 设置并发保护）',
    created_at                 DATETIME(6)  NOT NULL COMMENT '创建时间',
    updated_at                 DATETIME(6)  NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    -- 邮箱唯一约束（utf8mb4_unicode_ci 排序规则天然大小写不敏感）
    UNIQUE KEY uk_users_email (email),
    -- 数据库兜底（数据库是正确性来源）：大小写 + 空白规范化后仍唯一，
    -- 独立于应用层规范化，使"  Foo@Bar.COM " 与 "foo@bar.com" 视为同一邮箱。
    email_normalized           VARCHAR(320) GENERATED ALWAYS AS (LOWER(TRIM(REGEXP_REPLACE(email, '[[:space:]]', '')))) STORED,
    UNIQUE KEY uk_users_email_normalized (email_normalized)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '用户';

-- 用户会话表：只存会话令牌的 SHA-256 哈希，禁止明文令牌；
-- 保存过期时间、最后访问时间（滚动续期依据，§3.4）、撤销时间与设备摘要（DEC-02、backend §4.2）。
CREATE TABLE user_sessions (
    id               CHAR(36)     NOT NULL COMMENT '会话 ID（UUID）',
    user_id          CHAR(36)     NOT NULL COMMENT '所属用户（外键 users.id）',
    token_hash       CHAR(64)     NOT NULL COMMENT '会话令牌 SHA-256 十六进制哈希，禁止明文令牌',
    expires_at       DATETIME(6)  NOT NULL COMMENT '过期时间（默认 30 天）',
    last_accessed_at DATETIME(6)  NOT NULL COMMENT '最后访问时间（最后访问超过 15 天时滚动续期）',
    revoked_at       DATETIME(6)  NULL COMMENT '撤销时间；NULL 表示未撤销',
    device_summary   VARCHAR(255) NULL COMMENT '设备摘要（登录时的设备/UA 描述）',
    created_at       DATETIME(6)  NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    -- 会话令牌哈希唯一：同一明文令牌只对应一条有效记录
    UNIQUE KEY uk_user_sessions_token_hash (token_hash),
    -- 按用户查询会话（登录恢复、撤销当前用户其余会话）
    KEY idx_user_sessions_user_id (user_id),
    -- 清理过期/已撤销会话（S9-BE-02 每日清理）
    KEY idx_user_sessions_revoked_expires (revoked_at, expires_at),
    CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '用户会话';
