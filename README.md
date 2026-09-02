# TimeClock 学习打卡系统

基于清单的学习打卡应用：创建刷题任务，每天按顺序完成指定数量的题目并写下文字题解，系统自动记录打卡、连续天数、月历与漏打补打。

- 后端：Java 21 + Spring Boot 3.3（Spring MVC / Security / JDBC / Flyway）+ MySQL 8
- 前端：React 19 + TypeScript 5 + Vite 6 + Ant Design 5 + TanStack Query 5
- 测试：JUnit 5（远程 MySQL 8 集成测试）、Vitest + React Testing Library + MSW、Playwright 双视口 E2E

## 功能总览

### 账户与安全

- 邮箱注册 / 登录，密码使用 Argon2id 哈希存储。
- 数据库 Session（HttpOnly Cookie）+ 全局 CSRF 保护；会话滚动续期，支持多端同时登录与单端登出。
- 登录失败按 IP + 邮箱限流。
- 所有资源按用户隔离，跨用户访问统一返回 404。

### 清单型任务

- 创建任务：名称、每日目标题数、开始/结束日期、IANA 时区；保存为草稿。
- 编辑、启用（draft → active）、物理删除（二次确认，级联删除条目与打卡事实）。
- 启用前置校验：任务必须至少有 1 个已确认题目，否则返回 422。

### 学习条目与 xlsx 导入

三种录入方式：

1. **单个添加**：逐条录入标题、内容、参考解析、链接、顺序号。
2. **粘贴批量导入**：粘贴文本 → 预览解析结果 → 确认入库（幂等键防重复提交）。
3. **xlsx 文件导入**：上传 `.xlsx` → 同步解析预览 → 简单标题去重（规范化后相同的标题默认跳过）→ 确认入库。首行必须是固定列头 `title, content, analysis, link, order`。

### 每日计划与自动分配

- 按任务时区计算每个计划日，题目按 `order` 顺序分配。
- 当日未完成的题目自动顺延到之后的日子；提前完成不计入当天。
- 每天展示的待做列表 = `min(每日目标, 剩余 pending 数)`。

### 完成与题解

- 每道题需填写非空白文字题解才能标记完成；可随时撤销（题解保留），重复完成幂等。
- **自动打卡**：当日最后一道题完成时，服务端在同一事务内自动完成该日打卡，前端无需额外操作。
- 所有写操作携带 `Idempotency-Key`，同键重放返回首次结果，不同请求体同键返回 409。

### 今日页

- 日期问候 + 五项汇总（今日任务 / 已完成 / 待完成 / 当前连续 / 最长连续）。
- 按任务展示四态：未开始 / 进行中 X/Y / 已完成 / 无计划。

### 日历、连续天数与补打

- 月历视图：每格显示当日合并状态（completed / partial / missed / makeup / 无计划），可按任务和状态筛选。
- 点击日期查看详情：进度、补打原因、当日题解摘要。
- 任务统计：当前连续、最长连续、已完成题数、预计完成日期。
- **漏打结算**：每小时定时（以及每次读取日历时）将过期计划日结算为 missed/partial。
- **补打**：过去 3 个自然日（不含今天）内 missed/partial 的日期，填写必填原因后补打；补打计入完成率但**不修复连续链**，且提交后不可撤销。

## 快速开始

### 环境要求

- JDK 21、Maven 3.9+
- Node.js ≥ 22、npm
- 可访问的 MySQL 8 实例（本地或远程均可）

### 配置数据库连接

连接信息全部通过环境变量注入，配置文件中不含任何凭据：

| 环境变量 | 说明 | 默认值 |
| --- | --- | --- |
| `TC_MYSQL_HOST` | MySQL 主机 | 必填 |
| `TC_MYSQL_PORT` | MySQL 端口 | 3306 |
| `TC_MYSQL_DATABASE` | 数据库名 | 必填 |
| `TC_MYSQL_USERNAME` | 用户名 | 必填 |
| `TC_MYSQL_PASSWORD` | 密码 | 必填 |

表结构由 Flyway 在启动时自动迁移（V1–V8），无需手工建表。

### 启动后端

```bash
cd backend
export TC_MYSQL_HOST=... TC_MYSQL_DATABASE=... TC_MYSQL_USERNAME=... TC_MYSQL_PASSWORD=...
mvn spring-boot:run
```

后端默认监听 `http://localhost:8080`，接口前缀 `/api/v1`。启动后可通过 Swagger UI 查看 API 文档（可用 `SPRINGDOC_ENABLED=false` 关闭）。

### 启动前端

```bash
cd frontend
npm install
npm run dev
```

开发服务器默认 `http://localhost:5173`，API 请求代理到后端 8080。

### 运行测试

```bash
# 后端集成测试（需要 TC_MYSQL_* 指向测试库，推荐独立库名如 time_clock_test）
cd backend && mvn test

# 前端单元 / 组件测试
cd frontend && npm test

# Playwright E2E（需先启动前后端）
cd frontend && npx playwright test
```

## API 概览

所有响应为 `{ data, requestId }` 信封格式；错误为 `{ error: { code, message }, requestId }`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/auth/register` | 注册 |
| POST | `/api/v1/auth/login` | 登录 |
| GET | `/api/v1/auth/me` | 当前用户 |
| POST | `/api/v1/auth/logout` | 登出当前会话 |
| GET | `/api/v1/auth/csrf` | 获取 CSRF Token |
| GET/POST | `/api/v1/tasks` | 任务列表 / 创建 |
| GET/PATCH/DELETE | `/api/v1/tasks/{taskId}` | 详情 / 编辑 / 物理删除 |
| POST | `/api/v1/tasks/{taskId}/activate` | 启用任务 |
| GET/POST | `/api/v1/tasks/{taskId}/items` | 条目列表 / 单个新增 |
| POST | `/api/v1/tasks/{taskId}/items/paste-preview` · `paste-confirm` | 粘贴导入预览 / 确认 |
| POST | `/api/v1/tasks/{taskId}/imports/xlsx/preview` · `confirm` | xlsx 导入预览 / 确认 |
| GET/PATCH | `/api/v1/items/{itemId}` | 条目详情 / 编辑 |
| GET/PUT | `/api/v1/items/{itemId}/submission` | 读取 / 保存文字题解 |
| POST | `/api/v1/items/{itemId}/complete` · `reopen` | 完成 / 撤销（幂等） |
| GET | `/api/v1/tasks/{taskId}/today-items` | 今日应做条目切片 |
| GET | `/api/v1/dashboard/today` | 今日总览（四态任务 + 连续摘要） |
| GET | `/api/v1/tasks/{taskId}/checkins/{date}` | 某日打卡详情（五态） |
| GET | `/api/v1/calendar?month=YYYY-MM` | 月历（可按 taskId/filter 过滤） |
| GET | `/api/v1/tasks/{taskId}/stats` | 任务统计 |
| POST | `/api/v1/tasks/{taskId}/checkins/{date}/makeup` | 补打（窗口 / 原因必填 / 幂等） |

## 项目结构

```text
backend/
  src/main/java/com/timeclock/
    auth/        注册、登录、Session、CSRF、Argon2id、限流
    user         （并入 auth）
    task/        任务 CRUD、启用、计划日计算 TaskScheduleCalculator
    item/        学习条目 CRUD、题解、完成/撤销事务、今日切片
    importing/   xlsx 同步解析预览/确认（Apache POI）
    checkin/     打卡事实、月历、统计、漏打结算调度、补打
    schedule/    今日总览聚合、StreakCalculator 连续天数纯计算
    common/      统一异常、信封、幂等键服务、请求上下文
  src/main/resources/db/migration/   V1–V8 Flyway 迁移（不可变）

frontend/
  src/
    api/client.ts            统一 fetch 客户端（Cookie、CSRF、错误映射）
    app/                     路由与应用壳
    features/
      auth/                  登录 / 注册 / 受保护路由
      tasks/                 任务列表、创建/编辑表单
      items/                 条目页：题解编辑、完成/撤销
      imports/               xlsx 导入页
      dashboard/             今日页
      checkins/              月历页与补打抽屉
    test/                    Vitest + RTL + MSW 测试
    e2e/                     Playwright 用例（桌面 + 移动双视口）

memory-bank/                 规格文档（PRD、实现计划、架构、状态机等）
contracts/openapi/           OpenAPI 契约与校验脚本
```

## 关键设计约束

- **日期语义**：所有日期为 `YYYY-MM-DD`，计划日与结算均按**任务自己的 IANA 时区**计算，不做 UTC 重解释。
- **历史冻结**：编辑频率/目标/时区不改写过去的计划，仅影响尚未开始的计划日。
- **补打规则**：只针对过去 3 天内的 missed/partial；计入完成率但不连接连续链；一旦提交不可修改或撤销。
- **幂等与并发**：complete/reopen/makeup/导入确认均通过 `Idempotency-Key` 幂等；完成事务固定锁序（先锁任务行再写幂等键）避免死锁。
- **导入安全**：xlsx 去重永不覆盖已有条目；用户内容仅作为数据存储，不会被当作指令执行。

## 明确不支持（V1.0 范围外)

习惯型打卡、暂停/归档/软删除、提前完成、PDF/DOCX/OCR 解析、图片题解与文件存储、异步导入、复杂去重合并、数据导出、外部通知、多端编辑冲突解决。

## 更多文档

完整规格、决策记录与实现进度见 [`memory-bank/`](memory-bank/) 目录：
[PRD.md](memory-bank/PRD.md) · [implementation-plan](memory-bank/TimeClock-V1.0-implementation-plan.md) · [architecture.md](memory-bank/architecture.md) · [progress.md](memory-bank/progress.md)
