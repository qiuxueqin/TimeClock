# V1.0 仓库治理与目录所有权登记（S0-OPS-01 交付物）

> 本文档由 **S0-OPS-01「初始化仓库治理」** 生成。
> 对应测试：**TEST-S0-OPS-01-01（干净克隆审计）**。
> 所有者：部署 Agent。
> 用途：定义仓库目录边界、忽略规则、每个目录的职责，以及"共享文件"的唯一负责人（单所有权，§4.2-3）。后续任何 Agent 新增/改动共享文件前，必须按本登记确认负责人。

## 1. 仓库目录结构与职责

| 目录 | 职责 | 所有权 |
| --- | --- | --- |
| `/`（根） | 唯一根构建/配置入口；`.gitignore`、`CLAUDE.md`、`README.md` | 部署 Agent（根构建文件） |
| `contracts/` | API 契约（OpenAPI）、DTO、契约校验工具配置 | API Agent |
| `backend/` | Java 21 + Spring Boot 模块化单体 | 后端 Agent |
| `frontend/` | React 19 + Vite SPA | 前端 Agent |
| `deploy/` | Docker Compose、Nginx/Caddy 配置、环境模板、备份脚本 | 部署 Agent |
| `e2e/` | Playwright 端到端测试与公共夹具 | QA Agent |
| `memory-bank/` | 规范与追踪文档（PRD、实施计划、状态机、需求追踪、进度、架构） | 协调 Agent |

## 2. 目录所有权与共享文件负责人（单所有权登记）

> 共享文件（§4.2-3）同一时刻只能由一个 Agent 修改。登记如下。

### 2.1 根共享文件

| 文件 | 负责人 | 说明 |
| --- | --- | --- |
| `.gitignore` | 部署 Agent | 仓库忽略规则 |
| `CLAUDE.md` | 协调 Agent | 工程指引 |
| `README.md` | 部署 Agent | 工程入口说明 |

### 2.2 contracts/

| 文件/目录 | 负责人 | 说明 |
| --- | --- | --- |
| `openapi/` | API Agent | OpenAPI 契约（单一 owner 维护） |

### 2.3 backend/（模块化单体）

| 模块/文件 | 负责人 | 说明 |
| --- | --- | --- |
| `pom.xml` | 后端 Agent（根构建单 owner） | 依赖与构建配置 |
| `src/main/java/.../auth/` | 后端 Agent | 注册/登录/登出/Session |
| `src/main/java/.../user/` | 后端 Agent | 用户资料、时区、偏好 |
| `src/main/java/.../task/` | 后端 Agent | 任务生命周期 |
| `src/main/java/.../schedule/` | 后端 Agent | 计划日/分配/顺延/连续 |
| `src/main/java/.../item/` | 后端 Agent | 学习条目 |
| `src/main/java/.../submission/` | 后端 Agent | 题解/图片/撤销 |
| `src/main/java/.../file/` | 后端 Agent | 上传/哈希/私有下载（S4 建立骨架，S7 泛化） |
| `src/main/java/.../importing/` | 后端 Agent | 导入批次/候选/去重 |
| `src/main/java/.../job/` | 后端 Agent | 后台任务/定时清理 |
| `src/main/java/.../audit/` | 后端 Agent | 幂等/审计/版本冲突 |
| `src/main/resources/db/migration/` | 数据库 Agent（迁移目录单 owner） | Flyway 迁移（不可改写） |

### 2.4 frontend/（SPA）

| 目录 | 负责人 | 说明 |
| --- | --- | --- |
| `package.json` / 根构建 | 前端 Agent（根构建单 owner） | 依赖与脚本 |
| `src/app/` | 前端 Agent | 路由、Provider、错误边界 |
| `src/api/` | 前端 Agent（公共 API 客户端单 owner） | fetch 客户端、端点、DTO |
| `src/features/*/` | 前端 Agent | 各业务 feature |
| `src/components/` | 前端 Agent | 通用组件 |
| `src/hooks/` | 前端 Agent | 通用 hooks |
| `src/lib/` | 前端 Agent | 工具/常量 |
| `src/styles/` | 前端 Agent | 全局样式 |
| `src/test/` | QA Agent（E2E 公共夹具单 owner） | MSW handlers、fixtures |

### 2.5 共享/协作文件

| 文件 | 负责人 | 说明 |
| --- | --- | --- |
| 全局状态枚举（`state-machine-and-time-rules-V1.0.md`） | 协调 Agent + API Agent | 前后端/DB 状态统一基线 |
| OpenAPI 契约 | API Agent | 契约变更唯一 owner |
| 迁移目录 | 数据库 Agent | 已合并迁移不可改写 |
| E2E fixtures | QA Agent | 公共夹具 |
| 根构建文件 | 后端 Agent（pom）/ 前端 Agent（package.json） | 各自根构建单 owner |

## 3. 忽略规则（.gitignore 已建立）

`.gitignore` 已排除：

- **IDE 私有配置**：`.idea/`、`*.iml`、`.vscode/`
- **构建产物**：`target/`、`node_modules/`、`dist/`、`build/`、`*.class/jar/war`
- **环境秘密与运行时文件**：`.env`、`.env.local`、`*.pem/key`、`secrets/`、`storage/`、`data/`、`uploads/`、`app-files/`
- **操作系统**：`.DS_Store`、`Thumbs.db`

> 环境模板以 `*.example.env` 形式保留供参考，不提交真实秘密。文件存储卷目录一律忽略，运行时由环境变量 `APP_STORAGE_ROOT` 指定（backend §6.2）。

## 4. 干净克隆审计结论（TEST-S0-OPS-01-01）

在全新克隆上检查（对应 §2 的验证点）：

1. **目录可识别**：根目录含 `contracts/`、`backend/`（待建）、`frontend/`（待建）、`deploy/`（待建）、`e2e/`（待建）、`memory-bank/` 及根共享文件。✓（尚未建立的目录由后续 S0-BE-01/FE-01/OPS-02 创建）
2. **IDE 私有文件不进变更列表**：`.idea/`、`*.iml` 已被 `.gitignore` 忽略，`git status` 不再显示。✓
3. **环境秘密和运行文件不会被跟踪**：`.env*`、`secrets/`、`storage/` 等已忽略，仅有 `*.example.env` 白名单可提交。✓

**审计通过。** 目录所有权与共享文件负责人已登记，见 §2。

## 5. 未决项

当前未决项为 **0**。
