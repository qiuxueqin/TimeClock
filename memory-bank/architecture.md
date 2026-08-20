# 架构说明（V1.0 精简版）

> 本文件说明当前代码库与文档架构。实施计划是唯一可执行蓝图。

## 1. 当前进度

- S0：规格、工程骨架、CI、远程 MySQL 测试基础已完成并通过 GATE-S0。
- S1：用户/会话数据库模型、精简范围 V3 修正迁移与邮箱注册已完成；登录、CSRF、会话恢复仍待完成。
- 精简后的后续主线：S1 → S2 任务 → S3 条目/xlsx → S4 题解/自动打卡 → S5 今日页 → S6 日历/连续/补打 → S7 部署验收。

## 2. 文档地图

| 文件 | 作用 |
| --- | --- |
| `PRD.md` | 精简产品需求事实来源：仅清单型刷题 |
| `TimeClock-V1.0-implementation-plan.md` | 精简实施蓝图：REQ/DEC/S0~S7/TEST/GATE |
| `backend-tech-stack-V1.0.md` | Spring Boot、MySQL、POI xlsx 架构 |
| `frontend-tech-stack-V1.0.md` | React SPA、任务/条目/xlsx/日历架构 |
| `requirements-tracking-V1.0.md` | REQ/DEC 与步骤、测试的双向追踪 |
| `state-machine-and-time-rules-V1.0.md` | 任务、条目、打卡状态与时间规则 |
| `progress.md` | 已完成步骤和当前进度 |
| `architecture.md` | 本文件 |

## 3. 系统架构

```text
浏览器（React SPA）
        │ HTTPS + Session Cookie + CSRF
        ▼
Spring Boot 单体应用
  auth / user / task / schedule / item / checkin / importing / audit
        │ JDBC + Flyway
        ▼
MySQL 8（远程实例）
```

没有文件存储卷：xlsx 在请求中解析，正式数据只有数据库记录；用户题解为 `learning_items.solution_text`。

## 4. 数据模型摘要

- `users`、`user_sessions`：认证。V3 精简修正后，`users` 保留 `id`、`email`、`password_hash`、`timezone`、`status`、审计字段及邮箱规范化唯一约束；不再包含 `overdue_reminder_visible`、`version`。
- `tasks`：仅 `draft`/`active` 清单任务、daily 目标、任务时区、日期边界。
- `learning_items`：题目、解析、顺序、pending/completed、文字题解。
- `checkins`：按任务+日期唯一，completed/partial/missed/makeup。
- `idempotency_keys`：完成、撤销、补打、xlsx 确认防重复。
- `audit_logs`：最小操作审计，不记录题解全文或认证秘密。

## 5. 关键不变量

1. 所有资源查询和写操作校验当前用户归属。
2. 同任务同计划日最多一条打卡。
3. 题解去空白后为空不可完成。
4. 完成最后目标时服务端自动打卡，前端无第二次打卡请求。
5. 未完成条目下一计划日优先顺延；无提前完成。
6. partial/missed 中断连续，makeup 不连接或修复。
7. xlsx 预览未确认前不写正式条目；重复标题默认跳过。
8. 任务删除为物理删除并级联清理关联数据。

## 6. 明确不进入当前架构

习惯型、PDF/DOCX/OCR、图片题解、文件存储、异步解析、暂停/归档、软删除、提前完成、复杂导出、外部通知、Redis/MQ/微服务。
