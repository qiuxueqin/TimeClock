-- S6-DB-01：补打事实完整性。makeup 状态必须携带补打原因（DEC-10）。
-- 迁移不可改写；checkins (task_id, checkin_date) 唯一与状态 CHECK 已由 V7 建立。
ALTER TABLE checkins
    ADD CONSTRAINT chk_checkins_makeup_reason
    CHECK (status <> 'makeup' OR (makeup_reason IS NOT NULL AND TRIM(makeup_reason) <> ''));
