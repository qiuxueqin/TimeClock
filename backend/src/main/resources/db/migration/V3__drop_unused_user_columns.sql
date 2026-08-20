-- S1-DB-02 精简范围修正迁移
-- 迁移不可改写；移除 V1.0 不再使用的 users 列。
ALTER TABLE users
    DROP COLUMN overdue_reminder_visible,
    DROP COLUMN version;
