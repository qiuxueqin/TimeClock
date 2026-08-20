# 进度追踪（V1.0 精简版）

## 阶段门禁状态

| 阶段 | 状态 | 备注 |
| --- | --- | --- |
| S0 规格、工程骨架与质量门禁 | ✅ 已完成 | 原 GATE-S0 已通过；精简规格文档已重构 |
| S1 认证、会话与用户隔离 | 🔄 进行中 | S1-DB-01、S1-DB-02、S1-BE-01 已完成；下一步 S1-BE-02 登录 |
| S2 清单任务与 daily 计划 | ⏸ 未开始 | 依赖 S1 |
| S3 条目、xlsx 导入、分配顺延 | ⏸ 未开始 | 依赖 S2 |
| S4 文字题解、完成/撤销、自动打卡 | ⏸ 未开始 | 依赖 S3 |
| S5 今日页 | ⏸ 未开始 | 依赖 S4 |
| S6 日历、连续天数、补打 | ⏸ 未开始 | 依赖 S4、S5 |
| S7 部署、安全与全链路验收 | ⏸ 未开始 | 依赖 S6 |

## 已完成步骤

### S0 阶段

- S0-ARC-01 需求追踪与决策登记：已完成，原始审计通过。
- S0-ARC-02 状态机与时间规则：已完成；精简范围已重新冻结。
- S0-API-01 OpenAPI 基线：已完成；后续需按精简契约删除无关端点。
- S0-OPS-01 仓库治理：已完成；精简文档已同步。
- S0-BE-01 Java 21 后端骨架：已完成。
- S0-DB-01 MySQL/Flyway 基线：已完成，连接远程测试库。
- S0-FE-01 React 前端骨架：已完成。
- S0-QA-01 分层测试：已完成；精简夹具规则已同步。
- S0-OPS-02 CI 门禁：已完成；精简门禁文档已同步。

### S1 阶段

- S1-DB-01 用户与会话模型：已完成，迁移 `V2__user_session.sql`。
- S1-DB-02 精简范围修正迁移：已完成，新增 `V3__drop_unused_user_columns.sql` 移除 `users.overdue_reminder_visible` 与 `users.version`；注册代码、UserView、Flyway/数据库/注册测试已同步，并在独立远程 MySQL 8 `time_clock_test` 上验证通过。
- S1-BE-01 邮箱注册：已完成，Argon2id、邮箱规范化、限流和 API 测试通过。

## 精简范围修正

- S1-DB-02 已完成：新增 Flyway V3 修正迁移移除精简 V1.0 不再使用的 `users.overdue_reminder_visible` 与 `users.version`，并同步认证代码、UserView 及测试；独立远程 MySQL 8 测试库验收通过。

## 下一步

- [x] S1-DB-02 精简范围修正迁移：移除 `users.overdue_reminder_visible` 与 `users.version`，并同步认证代码与测试；远程 MySQL 验收通过。
- [ ] S1-BE-02 实现登录与数据库 Session（依赖 S1-DB-02）。
- [ ] S1-BE-03 实现当前用户、登出和续期。
- [ ] S1-BE-04 强制 CSRF。
- [ ] S1-BE-05 资源归属拦截。
- [ ] S1-FE-01 统一 API 客户端。
- [ ] S1-FE-02 注册/登录/会话恢复页面。
- [ ] S1-QA-01 多端会话与隔离 E2E。
- [ ] GATE-S1。

## 精简变更记录

- V1.0 仅清单型任务，删除习惯型。
- 题目导入改为 xlsx 同步解析、预览、简单标题去重、确认入库。
- 删除 PDF/DOCX/OCR、图片题解、文件存储、异步导入和复杂去重。
- 保留打卡记录、连续天数、月历和补打。
- 删除暂停/归档/软删除/提前完成，任务删除改为物理删除。
- 认证保留邮箱、Session、CSRF；去掉复杂多端编辑冲突和导出。

## 参考

- `memory-bank/PRD.md`
- `memory-bank/TimeClock-V1.0-implementation-plan.md`
- `memory-bank/requirements-tracking-V1.0.md`
- `memory-bank/state-machine-and-time-rules-V1.0.md`
