# V1.0 持续集成门禁（精简版）

> 范围：清单型任务、xlsx 导入、文字题解、打卡/日历/补打。后续 Agent 不得绕过门禁合并。

## 1. CI 流水线

| Job | 门禁内容 |
| --- | --- |
| `contract` | OpenAPI 契约语法、引用、写接口 CSRF、幂等声明 |
| `backend` | Java 21 编译与后端单元测试 |
| `backend-integration` | 远程 MySQL 8 迁移、唯一约束、事务测试 |
| `frontend` | Node 22 类型检查、单元/组件测试、生产构建 |
| `e2e` | Playwright 核心流程：登录、任务、xlsx、完成、日历 |

> 远程 MySQL 连接以 GitHub Secrets 注入：`TC_MYSQL_HOST`、`TC_MYSQL_PORT`、`TC_MYSQL_USERNAME`、`TC_MYSQL_PASSWORD`、`TC_MYSQL_TEST_DATABASE`。不得写入代码或文档中的真实值。

## 2. 本地复现

| 门禁 | 命令 |
| --- | --- |
| 契约 | `python contracts/openapi/validate.py` |
| 后端编译/单元 | `cd backend && mvn -q test -Dtest=TimeClockApplicationTests` |
| 后端集成 | 配置 `TC_MYSQL_*` 后，`cd backend && mvn -q test -Dtest=FlywayMigrationTests` |
| 前端 | `cd frontend && npm ci && npm run typecheck && npm test && npm run build` |
| E2E | `cd frontend && npm run build && npx playwright test` |

## 3. 规则

- 正常基线全部通过后才可合并。
- 临时失败测试、编译错误、迁移错误和契约错误必须阻断对应 job。
- 根构建文件、OpenAPI、Flyway 迁移和 E2E fixtures 遵循单一所有者。
- 远程测试库与生产/开发库必须隔离。

## 4. 未决项

当前未决项为 **0**。
