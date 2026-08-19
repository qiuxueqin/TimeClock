# V1.0 持续集成门禁（S0-OPS-02 交付物）

> 本文档由 **S0-OPS-02「建立持续集成门禁」** 生成。
> 对应测试：**TEST-S0-OPS-02-01（CI 复现）**。
> 所有者：部署 Agent。
> 用途：登记 CI 流水线（`.github/workflows/ci.yml`）的门禁组成、本地复现命令，以及"后续 Agent 不得绕过或关闭门禁来合并"的规则。

## 1. CI 流水线（GitHub Actions）

| Job | 门禁内容 | 故障注入点 |
| --- | --- | --- |
| `contract` | OpenAPI 契约校验（`contracts/openapi/validate.py`） | 修改契约产生未解析引用/缺 CSRF → 阻断 |
| `backend` | Java 21 编译 + 冒烟单元测试（不含远程 MySQL） | 编译错误 / 失败测试 → 阻断 |
| `backend-integration` | 远程 MySQL 8 集成测试（重建测试库 → Flyway 迁移） | 迁移错误 / 连接失败 → 阻断 |
| `frontend` | 类型检查 + 单元/组件测试 + 生产构建 | 类型错误 / 失败测试 / 构建错误 → 阻断 |
| `e2e` | Playwright 桌面+移动 E2E | E2E 断言失败 → 阻断 |

> `backend-integration` 需要协调者提供的远程 MySQL 连接，作为仓库 secret 注入（`TC_MYSQL_HOST` 等）。这些 secret 不进入代码库。

## 2. 本地复现命令（TEST-S0-OPS-02-01）

每个 job 都可在本地独立复现：

| 门禁 | 本地命令 |
| --- | --- |
| 契约 | `python contracts/openapi/validate.py` |
| 后端编译/单元 | `cd backend && mvn -q test -Dtest=TimeClockApplicationTests` |
| 后端集成 | `export TC_MYSQL_*=...; cd backend && mvn -q test -Dtest=FlywayMigrationTests`（先重建测试库） |
| 前端类型/测试/构建 | `cd frontend && npm run typecheck && npm test && npm run build` |
| E2E | `cd frontend && npm run build && npx playwright test` |

## 3. 故障门禁证据

在本地分别模拟故障，确认对应门禁会阻断：

| 人为故障 | 阻断门禁 | 证据 |
| --- | --- | --- |
| 契约缺 CSRF | `contract` | `validate.py` 报 `[ERROR] 写接口缺 CSRF`（S0-API-01 已实际捕获并修复 register/login） |
| 前端失败测试 | `frontend` | 临时 `Home.failing.test.tsx` → `npm test` 报 1 failed，删除后恢复（S0-QA-01 已验证） |
| 编译错误 | `backend` | `mvn test` 编译失败即退出非零 |
| 迁移错误 | `backend-integration` | Flyway 迁移失败抛异常使 `mvn test` 失败 |

结论：正常基线全部通过；每种人为故障均在对应门禁被发现并阻断。

## 4. 规则

- **后续 Agent 不得绕过或关闭门禁来合并**。任何共享工程改动必须通过对应 CI job。
- 根构建文件（`pom.xml`、`package.json`）与契约、迁移目录、E2E 夹具的单所有权（见 `repo-governance-V1.0.md`）不得被绕过。

## 5. 未决项

当前未决项为 **0**。
