# V1.0 仓库治理与目录所有权登记（精简版）

## 1. 目录职责

| 目录 | 职责 | 所有权 |
| --- | --- | --- |
| `/` | `.gitignore`、`CLAUDE.md`、`README.md` | 部署/协调 Agent |
| `contracts/` | OpenAPI 契约与校验 | API Agent |
| `backend/` | Java 21 + Spring Boot 单体 | 后端 Agent |
| `frontend/` | React 19 + Vite SPA | 前端 Agent |
| `deploy/` | Compose、反向代理、环境模板 | 部署 Agent |
| `e2e/` | Playwright 测试与 fixtures | QA Agent |
| `memory-bank/` | PRD、实施计划、追踪、状态、进度、架构 | 协调 Agent |

## 2. 后端模块所有权

| 模块/文件 | 负责人 | 说明 |
| --- | --- | --- |
| `pom.xml` | 后端 Agent | 依赖与构建配置 |
| `auth/` | 后端 Agent | 注册/登录/登出/Session |
| `user/` | 后端 Agent | 用户信息与时区默认值 |
| `task/` | 后端 Agent | 清单任务生命周期 |
| `schedule/` | 后端 Agent | daily 分配、顺延、连续计算 |
| `item/` | 后端 Agent | 题目与文字题解 |
| `checkin/` | 后端 Agent | 打卡、日历、补打 |
| `importing/` | 后端 Agent | xlsx 解析、预览、去重、确认 |
| `audit/` | 后端 Agent | 幂等与必要审计 |
| `db/migration/` | 数据库 Agent | Flyway 迁移，不可改写 |

不建立 habit、file、submission、job 模块。

## 3. 前端所有权

| 目录 | 负责人 | 说明 |
| --- | --- | --- |
| `package.json` | 前端 Agent | 依赖与脚本 |
| `src/app/` | 前端 Agent | 路由与 Provider |
| `src/api/` | 前端 Agent | 公共 fetch 客户端与 DTO |
| `src/features/auth/` | 前端 Agent | 认证 |
| `src/features/dashboard/` | 前端 Agent | 今日页 |
| `src/features/tasks/` | 前端 Agent | 任务 |
| `src/features/items/` | 前端 Agent | 条目、题解、完成 |
| `src/features/imports/` | 前端 Agent | xlsx 导入 |
| `src/features/checkins/` | 前端 Agent | 日历、连续、补打 |
| `src/test/` | QA Agent | MSW handlers 与测试工具 |

## 4. 共享文件规则

- OpenAPI 仅 API Agent 修改。
- Flyway 迁移仅数据库 Agent 修改；已合并迁移不可改写，只能追加修正迁移。
- 公共 E2E fixtures 仅 QA Agent 修改。
- `state-machine-and-time-rules-V1.0.md` 由协调 Agent 与 API Agent 共同维护。
- 每个原子步骤一个中文 commit，仅在 `main` 推进。

## 5. 忽略规则

继续忽略 `.idea/`、构建产物、`node_modules/`、`.env*`、秘密文件和运行时目录。V1.0 无文件存储卷，无需 `APP_STORAGE_ROOT`。

## 6. 未决项

当前未决项为 **0**。
