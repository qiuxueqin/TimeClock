# 进度追踪

> 当前实施阶段与步骤状态。每个原子步骤完成后在此登记，供未来的开发 Agent 和协调 Agent 参考。

## 阶段门禁状态

| 阶段 | 状态 | 备注 |
| --- | --- | --- |
| S0 规格冻结、工程骨架与质量门禁 | ✅ **已通过 GATE-S0** | 全部 S0 步骤完成，门禁验证通过 |
| S1 认证、会话、CSRF 与用户隔离 | 🔄 进行中（1/9 步完成） | S1-DB-01 已完成；依赖 S0（已满足） |
| S2 任务生命周期与计划规则 | ⏸ 未开始 | 依赖 S1 |
| S3 学习条目、分配、顺延与提前完成 | ⏸ 未开始 | 依赖 S2 |
| S4 图文题解与清单打卡闭环 | ⏸ 未开始 | 依赖 S3 |
| S5 习惯打卡与今日页 | ⏸ 未开始 | 依赖 S2（集成依赖 S4） |
| S6 日历、统计、补打与站内提醒 | ⏸ 未开始 | 依赖 S4、S5 |
| S7 私有文件、后台任务与解析 | ⏸ 未开始 | 依赖 S1、S2 |
| S8 导入校对、去重与确认入库 | ⏸ 未开始 | 依赖 S3、S7 |
| S9 导出、全链路验收、部署与加固 | ⏸ 未开始 | 依赖 S6、S8 |

## 步骤日志（最新在上）

### 2026-08-19 — S1-DB-01 完成 ✅

- **步骤**：S1-DB-01 建立用户与会话模型
- **所有者**：数据库 Agent
- **交付物**：`backend/src/main/resources/db/migration/V2__user_session.sql`
- **内容**：
  - `users`：邮箱唯一（`uk_users_email` + 生成列 `email_normalized` 兜底大小写/空白规范化，数据库是正确性来源）、`password_hash`（Argon2id）、`timezone`、`overdue_reminder_visible`、`status`、`version`（乐观锁）、审计字段。
  - `user_sessions`：`token_hash` 唯一（SHA-256 十六进制）、`expires_at`、`last_accessed_at`、`revoked_at`、`device_summary`；按 `user_id` 与 `(revoked_at, expires_at)` 索引；FK 归属 `users`。
  - 禁止明文密码 / 明文会话令牌（schema 仅含哈希列）。
- **测试**：TEST-S1-DB-01-01（数据库集成，远程 MySQL 8 测试库）通过
  - 大小写变体邮箱拒绝（唯一约束）；空白变体邮箱拒绝（生成列兜底）
  - 并发插入相同邮箱 8 线程仅 1 条成功；重复会话令牌哈希拒绝
  - 无明文 password/token 列，存储值具备哈希形态
  - `FlywayMigrationTests`（TEST-S0-DB-01-01 回归）2 项通过：Successfully validated 2 migrations，无漂移
  - 后端全量 8 项测试通过，BUILD SUCCESS
- **提交**：`b9d8347` "S1-DB-01 建立用户与会话模型（TEST-S1-DB-01-01 集成测试通过）"
- **下游依赖**：S1-BE-01（邮箱注册，依赖 users 表）、S1-BE-02（登录与 Session，依赖 user_sessions 表）

### 2026-08-19 — S0-ARC-01 完成 ✅

- **步骤**：S0-ARC-01 建立需求追踪和决策登记
- **所有者**：协调 Agent
- **交付物**：`memory-bank/requirements-tracking-V1.0.md`
- **内容**：19 项 REQ + 24 项 DEC 登记；107 个原子步骤反向来源审计
- **测试**：TEST-S0-ARC-01-01（文档审计）通过
  - REQ 覆盖 19/19，DEC 覆盖 24/24，步骤覆盖 107/107
  - 无无步骤需求，无无来源步骤，未决项 = 0
- **提交**：`0b6b472` "S0-ARC-01 建立 V1.0 需求追踪与决策登记（TEST-S0-ARC-01-01 通过）"
- **下游依赖**：S0-ARC-02（冻结状态机与时间规则）、S0-API-01（OpenAPI 基线）依赖本表

## 待办

- [x] S0-ARC-01 建立需求追踪和决策登记
- [x] S0-ARC-02 冻结状态机与时间规则
- [x] S0-API-01 建立 OpenAPI 基线
- [x] S0-OPS-01 初始化仓库治理
- [x] S0-BE-01 初始化 Java 21 后端工程
- [x] S0-DB-01 建立 MySQL 与 Flyway 基线
- [x] S0-FE-01 初始化 React 前端工程
- [x] S0-QA-01 建立分层测试基础
- [x] S0-OPS-02 建立持续集成门禁
- [x] **GATE-S0（阶段门禁）— 已通过**
- [x] S1-DB-01 建立用户与会话模型
- [ ] S1-BE-01 实现邮箱注册（下一阶段第二步骤）

## 参考

- 需求追踪与决策登记：`memory-bank/requirements-tracking-V1.0.md`
- 实施计划：`memory-bank/TimeClock-V1.0-implementation-plan.md`
