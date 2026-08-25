# 架构说明（V1.0 精简版）

> 本文件说明当前代码库与文档架构。实施计划是唯一可执行蓝图。

## 1. 当前进度

- S0：规格、工程骨架、CI、远程 MySQL 测试基础已完成并通过 GATE-S0。
- S1：认证、数据库 Session、CSRF、登录注册前端和基础多端验收已完成；提交 `c04da74`。S2 资源接口必须复用当前用户上下文和归属约束；真实资源越权测试随资源模块落地。
- S2：任务契约、V4 任务模型、任务创建/读取/列表/编辑/删除、daily 计划计算和基础任务管理前端已实现；后端 46 项独立 MySQL 回归、OpenAPI 校验和前端 typecheck/test/build 已通过。S2 Gate 仍等待 S3 条目模型提供成功 activate 主链路，以及任务专属 MSW/RTL 和移动端 E2E 验收。
- S3：学习条目 V5、手工/粘贴条目、同步 POI xlsx 预览确认、标题去重、草稿启用条目检查、按任务时区返回 pending 顺序切片已实现；完整跨日顺延等待 S4/S6 的日期事实，前端条目/导入路由已加入。
- S4：文字题解与完成闭环已完成。`ItemService.complete/reopen` 在同一事务内更新条目、当日进度和 checkins，注入 Clock、固定锁序（先 tasks 行锁再写幂等键）并通过 `S4IdempotencyConcurrencyTests` 并发验收；V7 建立 `(task_id,checkin_date)` 唯一打卡事实；OpenAPI 清理为纯文字题解契约；前端提供启用/条目入口、题解编辑器、今日进度展示且全程无 /checkins 写请求；Playwright 全链路（桌面+移动）8/8 通过，远程 MySQL 全量回归 65/65 通过。跨日撤销的打卡归属留待 S6 日结/补打补齐。
- S5：今日页已完成。`schedule` 包提供 `GET /api/v1/dashboard/today` 只读聚合：按各任务 IANA 时区判定计划日并复用 TaskScheduleCalculator；纯函数 StreakCalculator 输出逐任务与全局连续摘要（仅 completed 计入、无计划日跳过、partial/missed 中断、makeup 不计不修复、今天未完成不计数不断链）；DashboardStatus 四态 notStarted/inProgress/completed/noPlan。前端 TodayPage 渲染日期问候、五项汇总、整体进度与四态列表，骨架/空态/错误重试齐备；ItemPage 完成/撤销后精确失效 dashboard today 缓存；组件测试与双视口 E2E 通过。

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

## 4.2 S4 完成闭环实现摘要

- `POST /items/{id}/complete|reopen` 要求 `Idempotency-Key` 请求头；缺失由 `ApiExceptionHandler.MissingRequestHeaderException` 处理器返回 400。幂等键先锁 tasks 行再写入（锁序固定），同键同请求回放首次响应快照，同键不同请求体 409。
- 完成校验链：归属(user)→题解 trim 非空→任务 active→今日为计划日→条目在按序分配切片内；任一失败稳定返回 SOLUTION_REQUIRED / TASK_NOT_ACTIVE / ITEM_NOT_TODAY / ITEM_ALREADY_COMPLETED。
- 达标自动 upsert 当日 checkins=completed（ON DUPLICATE KEY），未达标有完成为 partial；`today-items` 的 plannedCount=min(每日目标, 剩余pending+今日已完成)，保证当日分母稳定。
- 撤销保留 solution_text 并将 completed_at 置空；仅当今日仍为计划日时回退当日打卡（跨日事实归属由 S6 补齐）。
- 前端 `TaskListPage` 提供 draft"启用"与"条目"入口；`ItemPage` 在 /today 路由渲染 `data-testid="today-progress"` 进度并按 complete 响应提示打卡达成；全前端无任何 /checkins 写请求。
- E2E：`frontend/e2e/s4-completion.spec.ts` 全 UI 链路（注册→登录→建任务→录 5 题→启用→完成→零 checkins 写断言→刷新持久→撤销 4/5）；注册接口不建立会话，脚本在注册后显式登录。

## 4.3 S5 今日页实现摘要

- `schedule` 包 `TodayOverviewController/Service` 提供 `GET /api/v1/dashboard/today` 只读聚合：用户时区定展示日期，逐任务按自身 IANA 时区取今日并复用 `TaskScheduleCalculator.planDay`；`plannedCount=min(每日目标, 剩余 pending+今日已完成)` 与 ItemService.today 同口径；草稿、非计划日、剩余为零统一 noPlan 且 plannedCount=0。
- 纯函数 `StreakCalculator` 计算连续摘要：窗口 `[max(startDate, 首个打卡事实日), min(today, endDate)]`，仅 completed 计入，无计划日跳过不断链，partial/missed 中断，makeup 不计不修复，今天是计划日但未完成时不计数也不断链，剩余条目为零时收敛到末次事实日避免误报断链。全局 currentStreak/longestStreak 取全部任务最大值。
- 前端 `TodayPage`：问候+日期、五项 Statistic 汇总、整体进度条、四态 Tag 列表（noPlan 不进列表）、骨架/空态引导创建/错误重试；状态以文字+颜色双通道表达。
- 缓存一致性（GATE-S5）：`ItemPage` 完成/撤销成功后精确失效 `['dashboard','today']`；组件测试断言无关查询（如 tasks 列表）不被波及。
- E2E：`frontend/e2e/s5-today.spec.ts` 覆盖空态→未开始→进行中→已完成与连续摘要刷新，全程零 /checkins 写请求；Playwright 配置 expect.timeout=15s 适配远程库延迟。


- `users`、`user_sessions`：认证。V3 精简修正后，`users` 保留 `id`、`email`、`password_hash`、`timezone`、`status`、审计字段及邮箱规范化唯一约束；不再包含 `overdue_reminder_visible`、`version`。
- `tasks`：仅 `draft`/`active` 清单任务、daily 目标、任务时区、日期边界；V4 迁移增加 `user_id` 外键、同用户同名唯一约束、`(user_id, status)` 查询索引及状态/频率/目标/日期范围数据库约束。
- `learning_items`：V5 持久化任务条目 `title/content/analysis/external_url`、任务内唯一且从 1 开始的 `sort_order`、`pending/completed` 状态、`solution_text`、`completed_at` 与审计字段；通过 `task_id` 外键 `ON DELETE CASCADE` 随任务物理删除，并有任务+状态+顺序及任务标题索引。
- `checkins`：按任务+日期唯一，completed/partial/missed/makeup；V7 已建立，清单完成达标时由完成事务自动 upsert。
- `idempotency_keys`：V6 持久化导入确认与其他确认写操作的用户/任务范围请求哈希、首次响应和 30 天过期时间；S4 完成/撤销复用同一服务，同键同请求重放，同键不同请求返回冲突。
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
