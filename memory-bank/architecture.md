# 架构说明（V1.0 精简版）

> 本文件说明当前代码库与文档架构。实施计划是唯一可执行蓝图。

## 1. 当前进度

- S0：规格、工程骨架、CI、远程 MySQL 测试基础已完成并通过 GATE-S0。
- S1：认证、数据库 Session、CSRF、登录注册前端和基础多端验收已完成；提交 `c04da74`。S2 资源接口必须复用当前用户上下文和归属约束；真实资源越权测试随资源模块落地。
- S2：任务契约、V4 任务模型、任务创建/读取/列表/编辑/删除、daily 计划计算和基础任务管理前端已实现；后端 46 项独立 MySQL 回归、OpenAPI 校验和前端 typecheck/test/build 已通过。S2 Gate 仍等待 S3 条目模型提供成功 activate 主链路，以及任务专属 MSW/RTL 和移动端 E2E 验收。
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

## 4.1 S1 认证实现摘要

- `AuthService` 负责邮箱规范化、Argon2id 注册/登录、统一认证失败响应和 IP+邮箱失败限流。
- S2 `task` 模块复用 SessionAuthenticationFilter 的当前用户主体和 JdbcTemplate；创建固定为 draft，条目表交付前 activate 对空条目稳定返回 422，不查询不存在的 learning_items 表。
- S2 `schedule` 计算使用注入 Clock 与任务自身 IANA 时区，通过 LocalDate 判定 daily 计划日和预计完成日期；真实剩余条目数与顺延分配由 S3 接入。
- `SessionService` 生成高熵随机 Token；`user_sessions` 仅保存 SHA-256 哈希，并支持 30 天 TTL、撤销和超过 15 天访问间隔后的滚动续期。
- `SessionAuthenticationFilter` 从 `SESSION_ID` Cookie 恢复 active 用户，将 `AuthenticatedUser` 写入 Spring Security 上下文；应用使用无状态 Spring Security，不依赖容器 HttpSession。
- `AuthSecurityConfig` 启用 CSRF，匿名允许 `register/login/csrf`，`me/logout` 要求认证；写请求缺少或错误 CSRF 返回 403。
- 前端 `src/api/client.ts` 使用 `credentials: include`，CSRF Token 只保存在内存，403 可刷新 Token 后重试一次；认证页面位于 `src/features/auth/Auth.tsx`。
- S1 验收测试：`AuthSessionApiTests`、`AuthSecurityBoundaryTests`、`frontend/src/api/client.test.ts` 和 `frontend/e2e/auth.spec.ts`。


- `users`、`user_sessions`：认证。V3 精简修正后，`users` 保留 `id`、`email`、`password_hash`、`timezone`、`status`、审计字段及邮箱规范化唯一约束；不再包含 `overdue_reminder_visible`、`version`。
- `tasks`：仅 `draft`/`active` 清单任务、daily 目标、任务时区、日期边界；V4 迁移增加 `user_id` 外键、同用户同名唯一约束、`(user_id, status)` 查询索引及状态/频率/目标/日期范围数据库约束。
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
