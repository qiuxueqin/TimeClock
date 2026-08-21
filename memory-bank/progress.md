# 进度追踪（V1.0 精简版）

## 阶段门禁状态

| 阶段 | 状态 | 备注 |
| --- | --- | --- |
| S0 规格、工程骨架与质量门禁 | ✅ 已完成 | 原 GATE-S0 已通过；精简规格文档已重构 |
| S1 认证、会话与用户隔离 | ✅ 已完成 | S1 认证主链路、前端登录注册、CSRF、Session 生命周期与验收测试已交付；S1 以 c04da74 提交并进入 S2 |
| S2 清单任务与 daily 计划 | 🟡 实现完成，GATE-S2 待补齐 | S2 后端/数据库/计划计算/前端已实现；成功启用 E2E 依赖 S3 条目，前端任务专属组件/MSW 测试仍需补齐 |
| S3 条目、xlsx 导入、分配顺延 | 🟡 主要实现完成，GATE-S3 待远程 MySQL/E2E 验收 | V5 learning_items、V6 幂等表、条目 CRUD/粘贴、同步 POI xlsx 预览确认、持久化确认幂等、标题去重、activate 成功路径、today pending 顺序切片、后端 S3 专项 API 测试、前端 RTL/MSW 测试和前端入口已实现；完整跨日事实与 Playwright 闭环仍待后续 checkin 阶段/环境验收 |
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

### S2 阶段

- S2-API-01 任务契约：已完成。任务 API 已收敛为 checklist/daily、draft/active；启用路径为 `POST /api/v1/tasks/{taskId}/activate`；PATCH 不使用 version/If-Match-Version；DELETE 为 204 物理删除；契约校验脚本已增加 S2 静态断言。
- S2-DB-01 任务及计划规则模型：已完成。新增 `V4__tasks.sql` 和 `TaskMigrationTests`；覆盖用户外键、同用户同名唯一、状态/频率/目标/日期 CHECK、用户状态索引和稳定排序索引。
- S2-BE-01 创建与草稿启用：已完成。新增 task Controller/Service/DTO；创建固定为 draft；空草稿可保存；无条目 activate 稳定返回 `422 TASK_ACTIVATION_REQUIRES_ITEM`；跨用户统一 404；CSRF、同名冲突和字段校验已覆盖。
- S2-BE-02 读取、列表、编辑、删除：已完成。支持用户范围列表、状态筛选、分页、稳定排序、详情、局部 PATCH 和 204 物理删除；不存在/跨用户统一 `404 TASK_NOT_FOUND`；删除后不可读取。
- S2-BE-03 计划日和预计完成日期：已完成。新增纯 `TaskScheduleCalculator`；使用注入 `Clock`、任务 IANA 时区和 `LocalDate`；覆盖 daily、起止边界、月末、跨年、闰日、DST、剩余量缩减；真实条目剩余量与顺延由 S3 接入。
- S2-FE-01 任务管理前端：已完成基础实现。新增任务 API client、受保护路由、列表、创建/编辑表单、Zod 校验和删除二次确认；typecheck、Vitest 现有测试和生产构建通过。任务专属 MSW/RTL 验收测试及真实任务 E2E 尚未补齐。

### S2 验收证据

- 后端独立远程 MySQL 8 回归：`TC_MYSQL_DATABASE=time_clock_test mvn test`，46 tests passed。
- 前端：`npm run typecheck`、`npm test`、`npm run build` 均通过；现有 Vitest 为 3 个文件、4 个测试通过。
- OpenAPI：`python contracts/openapi/validate.py` 通过，38 paths、67 schemas。
- 格式检查：`git diff --check` 通过。
- 当前 S2 尚不能宣布 GATE-S2 完成：成功的 draft→active 主流程需要 S3 正式 learning_items；前端任务专属组件/MSW、移动端真实 E2E 和删除关联数据级联验收仍待补齐。


- S1-DB-01 用户与会话模型：已完成，迁移 `V2__user_session.sql`。
- S1-DB-02 精简范围修正迁移：已完成，新增 `V3__drop_unused_user_columns.sql` 移除 `users.overdue_reminder_visible` 与 `users.version`；注册代码、UserView、Flyway/数据库/注册测试已同步，并在独立远程 MySQL 8 `time_clock_test` 上验证通过。
- S1-BE-01 邮箱注册：已完成，Argon2id、邮箱规范化、限流和 API 测试通过。
- S1-BE-02 登录与数据库 Session：已完成，Argon2id 凭据验证、统一 401、IP+邮箱失败限流、随机 Token、SHA-256 数据库存储、安全 Cookie 和多端 Session 测试通过。
- S1-BE-03 当前用户、登出和续期：已完成，Session Filter、`/me`、当前会话撤销、过期校验和滚动续期已实现并测试。
- S1-BE-04 CSRF：已完成，Spring Security CSRF 已启用，匿名获取 Token、写请求保护和安全边界测试已覆盖。
- S1-BE-05 资源归属基础：已完成认证主体与用户 ID 上下文；S2 资源接口必须复用 user_id 归属查询/修改/删除约束。
- S1-FE-01 统一 API 客户端：已完成，Cookie、内存 CSRF、错误映射、403 刷新重试和客户端测试已交付。
- S1-FE-02 注册/登录/会话恢复页面：已完成，登录/注册互链、受保护路由、刷新恢复和登出已实现。
- S1-QA-01 多端会话与隔离 E2E：已完成基础 Playwright 双上下文用例；真实资源越权 E2E 待 S2 资源出现后按模板扩展。

- S3 已实现主要纵向切片：OpenAPI 已收缩为 pending/completed 和同步 xlsx preview/confirm；V5 `learning_items` 使用任务级序号唯一与 `ON DELETE CASCADE`；后端支持条目 CRUD、粘贴预览/确认、POI xlsx 预览/确认、规范化标题默认跳过和 draft→active 条目检查；today endpoint 在无 checkin 事实时按任务时区返回 pending 顺序切片；前端加入条目、xlsx 导入和今日入口。

1. **补齐 S2 验收缺口并决定是否通过 GATE-S2**：新增任务 feature 的 RTL/MSW 测试（空列表、分页、非法目标、删除取消/确认、失败保留输入、404/403 错误），修复并验证 DELETE 204 的前端路径；补充移动端 Playwright 任务页面无横向溢出。
2. **开始 S3-API-01**：冻结学习条目、手工/粘贴/xlsx 预览确认、顺序分配和顺延契约；明确正式条目的确认语义，解除 S2 activate 成功路径依赖。
3. **实施 S3-DB-01**：新增 `learning_items` 迁移，状态仅 `pending/completed`，任务内 `sort_order` 唯一，并定义任务删除时的关联清理策略。
4. **实施 S3-BE-01/S3-BE-02/S3-BE-03**：手工/粘贴条目、xlsx 同步解析预览、标题去重与确认入库；随后回接任务 activate 的已确认条目检查并补充 draft→active 成功 E2E。
5. S3 完成后再实施 S3-BE-04/S3-FE-02 的按序分+配、未完成顺延和今日条目页面。


- S1-DB-02 已完成：新增 Flyway V3 修正迁移移除精简 V1.0 不再使用的 `users.overdue_reminder_visible` 与 `users.version`，并同步认证代码、UserView 及测试；独立远程 MySQL 8 测试库验收通过。

## 下一步

- [x] S1-DB-02 精简范围修正迁移：移除 `users.overdue_reminder_visible` 与 `users.version`，并同步认证代码与测试；远程 MySQL 验收通过。
- [x] S1-BE-02 实现登录与数据库 Session。
- [x] S1-BE-03 实现当前用户、登出和续期。
- [x] S1-BE-04 强制 CSRF。
- [x] S1-BE-05 资源归属基础。
- [x] S1-FE-01 统一 API 客户端。
- [x] S1-FE-02 注册/登录/会话恢复页面。
- [x] S1-QA-01 多端会话与隔离 E2E 基础验收。
- [x] GATE-S1：认证主链路与前端基础验收通过；真实业务资源越权测试随 S2 资源接口复用模板执行。

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
