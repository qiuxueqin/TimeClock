# 进度追踪（V1.0 精简版）

## 阶段门禁状态

| 阶段 | 状态 | 备注 |
| --- | --- | --- |
| S0 规格、工程骨架与质量门禁 | ✅ 已完成 | 原 GATE-S0 已通过；精简规格文档已重构 |
| S1 认证、会话与用户隔离 | ✅ 已完成 | S1 认证主链路、前端登录注册、CSRF、Session 生命周期与验收测试已交付；S1 以 c04da74 提交并进入 S2 |
| S2 清单任务与 daily 计划 | 🟡 实现完成，GATE-S2 待补齐 | S2 后端/数据库/计划计算/前端已实现；成功启用 E2E 依赖 S3 条目，前端任务专属组件/MSW 测试仍需补齐 |
| S3 条目、xlsx 导入、分配顺延 | 🟡 主要实现完成，GATE-S3 待远程 MySQL/E2E 验收 | V5 learning_items、V6 幂等表、条目 CRUD/粘贴、同步 POI xlsx 预览确认、持久化确认幂等、标题去重、activate 成功路径、today pending 顺序切片、后端 S3 专项 API 测试、前端 RTL/MSW 测试和前端入口已实现；完整跨日事实与 Playwright 闭环仍待后续 checkin 阶段/环境验收 |
| S4 文字题解、完成/撤销、自动打卡 | ✅ 已完成 | V7 checkins、题解保存/完成/撤销契约与事务（注入 Clock、任务/条目行锁、幂等键先锁后写）、并发幂等专项测试、前端完成闭环（启用/条目入口/今日进度）及 Playwright 全链路 E2E（桌面+移动 8/8）已交付；远程 MySQL 全量回归 65/65 通过 |
| S5 今日页 | ✅ 已完成 | S5-API-01 契约冻结（DashboardStatus 四态 + TodayTask 连续摘要）；S5-BE-01 `GET /api/v1/dashboard/today` 聚合（任务时区计划判定复用 TaskScheduleCalculator、纯 StreakCalculator 连续摘要、跨用户隔离与 401）；S5-FE-01 今日页（日期问候/汇总/四态列表/骨架空错误重试）、完成/撤销精确失效今日缓存、组件测试与双视口 E2E 已交付 |
| S6 日历、连续天数、补打 | ✅ 已完成 | S6-API-01 契约冻结（CheckinView 五态、MakeupRequest 仅原因必填）；S6-DB-01 V8 makeup 原因 CHECK；S6-BE-01 StreakCalculator 表驱动 19 用例；S6-BE-02 月历/详情/统计；S6-BE-03 漏打结算（每小时调度 + 读取时结算）；S6-BE-04 补打（窗口/原因/幂等/409 不可逆）；S6-FE-01 月历页与补打交互；S6-QA-01 双视口全链路 E2E 通过 |
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

### S4 阶段

- S4-API-01 题解与完成契约：已完成。OpenAPI 按精简规格清理了 submission 版本/历史、图片、`early` 提前完成与习惯型打卡写路径（25 paths / 55 schemas）；冻结 `GET|PUT /items/{id}/submission`、`POST /items/{id}/complete`、`POST /items/{id}/reopen`；错误码 SOLUTION_REQUIRED(422)、ITEM_NOT_TODAY(422)、ITEM_ALREADY_COMPLETED/ITEM_NOT_COMPLETED(409)、IDEMPOTENCY_CONFLICT(409)、缺失 Idempotency-Key(400)；paste-confirm 幂等键改为必填。
- S4-DB-01 打卡事实：已完成。新增不可变迁移 `V7__checkins.sql`：`(task_id,checkin_date)` 唯一、状态/数量 CHECK、任务删除级联；复用 V6 `idempotency_keys` 存请求哈希与首次响应快照。
- S4-BE-01 完成事务：已完成。`ItemService.complete/reopen` 注入 Clock、固定锁序（先 tasks 行 X 锁再写幂等键，消除外键 S→X 升级死锁 1213）、锁定任务/条目后校验归属+启用+计划窗口+今日分配，条目/进度/checkin 同事务更新；达标自动 upsert `completed`，未达标为 `partial`；撤销保留题解、completed_at 置空并回退当日打卡。
- S4-BE-02 完成幂等：已完成。同键同请求回放首次响应；同键不同请求体 409；十线程并发专项测试（`S4IdempotencyConcurrencyTests` 7 用例）覆盖同键并发、异键同条目恰好一次、跨用户 404、缺失键 400、非今日 422 与撤销回放。
- S4-BE-03 撤销完成：已完成。5/5→4/5 partial、题解保留、重复撤销不二次扣减；跨日打卡事实归属留待 S6 日结/补打补齐。
- S4-FE-01 前端闭环：已完成。client 移除 version/early/fileApi 并新增 taskApi.activate；TaskListPage 增加"启用"按钮与"条目"入口；ItemPage 提供题解编辑、参考解析展示（仅 analysis 非空）、完成/撤销按钮（pending 或空白题解禁用）、today 路由渲染"今日进度：X/Y"；全程无任何 /checkins 写请求。
- S4-QA-01 全链路 E2E：已完成。`e2e/s4-completion.spec.ts` 覆盖注册登录→建任务（每日目标 5）→录入 5 题→启用→空题解客户端拦截→逐项完成进度 1..5/5→断言零 /checkins 写请求→刷新持久 5/5→撤销回 4/5 且题解保留；desktop+mobile 双视口通过；顺带修复过期 smoke/auth E2E 使全套件 8/8 通过。

### S4 验收证据

- 后端远程 MySQL 8 全量回归：`TC_MYSQL_DATABASE=time_clock_test mvn test`，65 tests passed, 0 failures（含 S3/S4 API、并发幂等、迁移与认证回归）。
- Playwright 真实前后端 + 测试库链路：8 passed（s4-completion desktop/mobile、auth desktop/mobile、smoke desktop/mobile）。
- 前端：`npm run typecheck`、`npm test -- --run`（6 文件 17 测试）、`npm run build` 通过。
- OpenAPI：`python contracts/openapi/validate.py` 通过，25 paths、55 schemas。
- 格式检查：`git diff --check` 通过。

### S5 阶段

- S5-API-01 冻结今日总览契约：已完成。DashboardStatus 收敛为 notStarted/inProgress/completed/noPlan 四态；TodayTask 携带逐任务 currentStreak；总览携带全局 currentStreak/longestStreak；契约校验脚本增加 S5 静态断言（提交 13bb425）。
- S5-BE-01 实现今日总览聚合：已完成。新增 `GET /api/v1/dashboard/today`：按每个任务 IANA 时区计算今日计划并复用 TaskScheduleCalculator；plannedCount 与 ItemService.today 同口径（min(每日目标, 剩余 pending + 今日已完成)）；纯 `StreakCalculator` 按 [max(startDate, 首事实日), min(today, endDate)] 窗口计算连续（仅 completed 计入、无计划日跳过、partial/missed 中断、makeup 不计不修复、今天未完成不计数不断链、剩余为零收敛尾部）；草稿/非计划日/剩余为零统一 noPlan 且 plannedCount=0；跨用户隔离与未登录 401 已覆盖。
- S5-FE-01 实现今日页：已完成。日期+问候+五项汇总 Statistic+整体进度条+四态 Tag 任务列表（noPlan 不进列表）+骨架/空态（引导创建）/错误重试；ItemPage 完成/撤销成功后精确失效 `['dashboard','today']` 缓存（GATE-S5 缓存刷新要求），组件测试断言不波及无关查询；Playwright 配置放宽 expect 超时以适配远程库延迟，修复 auth spec 模块级邮箱在双 project 并发下的撞唯一约束竞态。

### S5 验收证据

- 后端远程 MySQL 8 全量回归：`TC_MYSQL_DATABASE=time_clock_test mvn test`，88 tests passed, 0 failures（新增 StreakCalculatorTest 16 用例表驱动连续规则 + S5TodayOverviewApiTests 7 用例聚合/隔离/401）。
- Playwright 真实前后端链路：10 passed ×2 连续运行（s5-today desktop/mobile 新增：空态→建任务→录入→启用→未开始→完成 1 题 inProgress 1/2→最后一项自动打卡 completed 2/2 连续 1 天，全程零 /checkins 写请求；s4-completion、auth、smoke 双视口回归通过）。
- 前端：`npm run typecheck`、`npm test -- --run`（7 文件 22 测试）、`npm run build` 通过。
- OpenAPI：`python contracts/openapi/validate.py` 通过，25 paths、55 schemas。
- 格式检查：`git diff --check` 通过。

### GATE-S5 结论

- 今日总览正确聚合清单任务与连续摘要：✅（混合状态、连续事实、隔离、401 测试通过）。
- 任何打卡成功后精确刷新今日及相关详情缓存：✅（complete/reopen 失效 dashboard/today，组件测试覆盖）。
- 移动与桌面组件测试通过：✅（Vitest 组件测试 + Playwright desktop/mobile 双视口）。

### S6 阶段

- S6-API-01 冻结日历/统计/补打契约：已完成。`GET /api/v1/calendar`（month 必填、taskId/filter 可选）、`GET /api/v1/tasks/{id}/checkins/{date}` 五态 CheckinView（completed/partial/missed/makeup/noPlan）、`GET /api/v1/tasks/{id}/stats`、`POST .../checkins/{date}/makeup`（Idempotency-Key 必填、仅原因必填）；清理习惯型与导出残留并增加 S6 契约断言（提交 7dceffa）。
- S6-DB-01 补打事实约束：已完成。V8 迁移增加 `status='makeup'` 必须携带非空原因的 CHECK；迁移测试覆盖同日唯一、空原因拒绝与级联删除（提交 5a9038c）。
- S6-BE-01 连续天数表驱动补齐：已完成。新增撤销第二日断链、夹无计划日跳过与补打漏打日不修复场景，19 用例通过（提交 f369d59）。
- S6-BE-03 漏打结算：已完成。每小时按任务时区幂等结算过期计划日为 missed/partial，makeup 不可改写，跳过草稿与未来任务（提交 1e20bfb）；月历读取时对 active 任务同步执行 settleTask，避免依赖调度时点。
- S6-BE-02 实现月历、日期详情与任务统计：已完成。CalendarService 合并同日多任务取最差状态（missed>partial>makeup>completed）并求和计划/完成数，filter 白名单筛选，跨用户隔离与归属 404；TaskStatsService 复用 StreakCalculator 输出当前/最长连续、条目计数与预计完成日期（active 才有 estimate）；修复 RowCallbackHandler 形参 lambda 中误用 while(rs.next()) 跳过首行的问题。测试 TEST-S6-BE-04-01 窗口边界（今天/昨天/第 3 天/第 4 天/空原因/幂等回放/已补打 409/无事实 404）与 TEST-S6-BE-02-01 查询（跨月/filter/合并视图最差状态/跨用户隔离/统计连续）5 用例通过，全量回归 107/107。
- S6-FE-01 实现月历、详情与补打交互：已完成。CalendarPage 七列自绘月历网格，状态以文字+颜色+图标三通道表达（已完成/部分完成/已漏打/已补打/无计划）；月份 DatePicker、任务 Select（合并视图）、状态 filter 三控件联动 queryKey；点击日期打开 Drawer 详情（进度、补打原因、题解摘要）；仅窗口内 missed/partial 展示补打表单（原因必填+去空白+500 上限），警示文案明确"计入完成率但不计入连续打卡天数，也无法撤销"；补打成功精确失效 calendar/checkin-detail/dashboard today 缓存。组件测试 TEST-S6-FE-01-01 五用例覆盖跨月切换、五状态渲染、filter、空原因本地拦截+成功刷新缓存、已补打只读视图；antd Select/DatePicker 在 jsdom+fake timers 下的可靠交互模式（mousedown 开下拉、键盘输入+Enter 提交月份）已在测试中固化。
- S6-QA-01 全链路 E2E：已完成。`e2e/s6-calendar.spec.ts` 双视口验证 TEST-S6-QA-01-01 闭环：UI 创建任务（开始日期=今天-4）→录入 10 题→启用 → SQL 回拨 created_at 并播种前 3 个计划日 completed 事实 → 日历读取触发漏打结算形成昨日 missed → 选择任务、空原因拦截、有效原因补打成功（状态变 makeup、显示不可撤销提示）→ 完成今日 2 题（DEC-09 自动打卡）→ 断言今日页/详情接口/日历网格/统计接口四处一致：currentStreak=1（missed 断链后由今天重新起算）、longestStreak=3（makeup 不修复）、completedItemCount=8。

### S6 验收证据

- 后端远程 MySQL 8 全量回归：`TC_MYSQL_DATABASE=time_clock_test mvn test`，107 tests passed, 0 failures（新增 S6CheckinCalendarApiTests 5 用例 + 此前 S6-BE-01/DB-03 步骤累计）。
- Playwright 真实前后端链路：12 passed ×1 连续运行（s6-calendar desktop/mobile 新增；s4-completion、s5-today、auth、smoke 双视口回归全部通过）。
- 前端：`npm run typecheck`、`npx vitest run`（9 文件 28 测试，含 CalendarPage 5 用例）、`npm run build` 通过。
- OpenAPI：`python contracts/openapi/validate.py` 通过，24 路由、47 schema。

### GATE-S6 结论

- 月历、统计、日结、补打窗口、连续链测试全部通过：✅（远程 MySQL 107/107，组件与 API 测试齐备）。
- 今日、详情、日历和统计一致性 E2E 通过：✅（双视口 s6-calendar 断言四处一致）。
- 补打不能制造重复完成或修复连续链：✅（同键回放首次结果、已 makeup 409 CHECKIN_ALREADY_MADE_UP；E2E 断言 longestStreak 保持 3、currentStreak 由今日重新起算）。

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
